package explodingkittens.remote;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test disconnessione client.
 * Scenario testato:
 *   - 2 giocatori si connettono al GameMaster
 *   - Player2 smette di mandare heartbeat (simula disconnessione, PLAYER_TIMEOUT = 10000ms)
 *   - Dopo il timeout, il GameMaster rimuove Player2
 *   - Player1 deve ricevere WINNER e PLAYER_DISCONNECTED
 */
class PlayerDisconnectionTest {
    private ContainerController mainContainer;
    private ContainerController clientContainer;
    private Runtime rt;

    @BeforeEach
    void startJade() throws Exception {
        rt = Runtime.instance();
        rt.setCloseVM(false);

        Profile mainProfile = new ProfileImpl();
        mainProfile.setParameter(Profile.GUI, "false");
        mainContainer = rt.createMainContainer(mainProfile);

        Profile clientProfile = new ProfileImpl();
        clientProfile.setParameter(Profile.MAIN_HOST, "localhost");
        clientProfile.setParameter(Profile.MAIN_PORT, "1099");
        clientContainer = rt.createAgentContainer(clientProfile);
    }

    @AfterEach
    void stopJade() throws Exception {
        if (mainContainer != null) mainContainer.kill();
        if (clientContainer != null) try { clientContainer.kill(); } catch (Exception ignored) {}
    }

    /**
     * - Crea Master e due player e i relativi containers
     * - Aspetta che inizi la partita e che p2 smetta di inviare heartbeat
     * - Verifica che p1 venga dichiarato vincitore e che venga notificato della disconnessione
     * @throws Exception
     */
    @Test
    void testPlayerDisconnectionLeadsToWin() throws Exception {
        AgentController masterCtrl = mainContainer.createNewAgent(
                "GameMaster",
                "explodingkittens.remote.GameMasterAgent",
                new Object[]{ "2" }
        );
        masterCtrl.start();
        Thread.sleep(500);

        AgentController p1Controller = clientContainer.createNewAgent(
                "player1",
                TestPlayerAgent.class.getName(),
                new Object[]{ "ACTIVE" }
        );
        p1Controller.start();

        AgentController p2Controller = clientContainer.createNewAgent(
                "player2",
                TestPlayerAgent.class.getName(),
                new Object[]{ "DISCONNECT" }
        );
        p2Controller.start();

        TestPlayerAgent p1 = waitForInstance("player1", 10_000);
        TestPlayerAgent p2 = waitForInstance("player2", 10_000);

        boolean started = p1.waitFor(p1::isGameStarted, 10_000);
        assertTrue(started, "La partita non è iniziata entro il timeout");
        System.out.println("[Test] Partita iniziata.");

        long waitMs = 2_000 + 10_000 + 2_000 + 3_000;
        System.out.println("[Test] Attendo " + waitMs / 1000 + "s per il timeout di Player2...");

        boolean p1Won = p1.waitFor(p1::hasWon, waitMs);
        assertTrue(p1Won, "Player1 non ha ricevuto il messaggio WINNER dopo la disconnessione di Player2");

        boolean notified = p1.waitFor(p1::wasNotifiedOfDisconnection, 3_000);
        assertTrue(notified, "Player1 non è stato notificato della disconnessione di Player2");

        System.out.println("[Test] PASS — Player1 ha vinto dopo la disconnessione di Player2.");
    }

    /**
     * Aspetta che l'istanza dell'agente sia disponibile nel registry statico.
     */
    private TestPlayerAgent waitForInstance(String name, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            TestPlayerAgent instance = TestPlayerAgent.getInstance(name);
            if (instance != null) return instance;
            Thread.sleep(200);
        }
        return null;
    }
}