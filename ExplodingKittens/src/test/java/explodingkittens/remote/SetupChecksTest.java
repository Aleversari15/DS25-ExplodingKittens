package explodingkittens.remote;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import org.junit.jupiter.api.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Classe che testa i check iniziali che vengono fatti sul nickname e sulla possibilità di unirsi alla partita.
 */
public class SetupChecksTest {
    private Runtime rt;
    private AgentContainer mainContainer;

    @BeforeEach
    void setUp() throws Exception {
        rt = Runtime.instance();
        Profile p = new ProfileImpl();
        mainContainer = rt.createMainContainer(p);

        AgentController master = mainContainer.createNewAgent("GM",
                "explodingkittens.remote.GameMasterAgent", null);
        master.start();
    }

    @AfterEach
    void tearDown() {
        try {
            mainContainer.kill();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Testa che nessun giocatore possa unirsi alla partita con un nickname già utilizzato da uno dei giocatori registrati.
     * Scenario simulato:
     * - p1 si unisce alla partita con nickname "Player"
     * - successivamente p2 cerca di unirsi alla partita con nickname "Player"
     * - viene ricevuto il messaggio INVALID_NICKNAME
     * @throws Exception
     */
    @Test
    @DisplayName("Scenario: Nickname duplicato deve essere rifiutato")
    void testDuplicateNicknameScenario() throws Exception {
        String duplicateName = "Player";
        BlockingQueue<String> resultQueue = new ArrayBlockingQueue<>(1);

        AgentController p1 = mainContainer.createNewAgent(
                duplicateName,
                TestPlayerAgent.class.getName(),
                new Object[]{"ACTIVE"}
        );
        p1.start();
        Thread.sleep(1000);

        AgentController p2Checker = mainContainer.createNewAgent(
                "Checker_Attempt",
                "explodingkittens.remote.NicknameCheckerAgent",
                new Object[]{duplicateName, resultQueue}
        );
        p2Checker.start();

        String response = resultQueue.poll(5, TimeUnit.SECONDS);

        assertEquals(Messages.INVALID_NICKNAME, response,
                "Il server avrebbe dovuto rifiutare il nickname duplicato");
    }

    /**
     * Testa i controlli che vengono fatti dal NicknameCheckerAgent in fase di setup
     * per assicurarsi che la lobby non sia già piena.
     * Scenario:
     * - Aggiungiamo Player1 e Player2 alla lobby (size=2)
     * - Player3 prova ad unirsi alla partita
     * - Verifichiamo che la risposta ricevuta sia LOBBY_FULL
     * @throws Exception
     */
    @Test
    @DisplayName("Test Lobby Piena: il terzo giocatore viene rifiutato")
    void testLobbyFull() throws Exception {
        BlockingQueue<String> resultQueue = new ArrayBlockingQueue<>(1);

        mainContainer.createNewAgent("Player1", TestPlayerAgent.class.getName(), new Object[]{"ACTIVE"}).start();
        mainContainer.createNewAgent("Player2", TestPlayerAgent.class.getName(), new Object[]{"ACTIVE"}).start();
        Thread.sleep(1500);

        AgentController checker = mainContainer.createNewAgent(
                "Checker_Lobby_Full",
                "explodingkittens.remote.NicknameCheckerAgent",
                new Object[]{"Player3", resultQueue} // Nickname nuovo, ma lobby potenzialmente piena
        );
        checker.start();

        String response = resultQueue.poll(5, TimeUnit.SECONDS);

        assertEquals(Messages.LOBBY_FULL, response, "La lobby doveva essere piena per il terzo giocatore");
    }
}