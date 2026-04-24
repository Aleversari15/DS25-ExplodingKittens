package explodingkittens.remote;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test per la verifica delle interazioni tra Agenti.
 * Questa classe testa il ciclo di vita iniziale della partita, focalizzandosi sulla
 * comunicazione tra GameMasterAgent e TestPlayerAgent.
 */
public class AgentTest {

    private AgentContainer container;
    public static List<String> receivedMessages;

    @BeforeEach
    void setUp() {
        Runtime rt = Runtime.instance();
        rt.setCloseVM(false);
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_PORT, String.valueOf(1099 + (int)(Math.random() * 100)));
        container = rt.createMainContainer(p);
        receivedMessages = Collections.synchronizedList(new ArrayList<>());
    }

    @AfterEach
    void tearDown() {
        try {
            if (container != null) {
                container.kill();
                Thread.sleep(500);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * Verifica la procedura di JOIN di un singolo giocatore.
     *@throws Exception se si verificano errori nella creazione o avvio degli agenti.
     */
    @Test
    public void testPlayerJoinCommunication() throws Exception {

        AgentController master = container.createNewAgent(
                "GameMasterTest",
                "explodingkittens.remote.GameMasterAgent",
                new Object[]{"2"}
        );
        master.start();
        Thread.sleep(1000);

        AgentController player = container.createNewAgent(
                "GiocatoreTester",
                "explodingkittens.remote.TestPlayerAgent",
                new Object[]{"ACTIVE"}
        );
        player.start();

        TestPlayerAgent instance = null;
        long deadline = System.currentTimeMillis() + 5000;
        while (instance == null && System.currentTimeMillis() < deadline) {
            instance = TestPlayerAgent.getInstance("GiocatoreTester");
            Thread.sleep(100);
        }
        assertNotNull(instance, "TestPlayerAgent non si è registrato nel registry");

        boolean joined = instance.waitFor(instance::isJoinConfirmed, 5000);
        assertTrue(joined, "Il GameMaster non ha confermato il JOIN con JOINED");
    }

/**
 * Verifica l'avvio della partita al raggiungimento del numero di giocatori attesi.
 */
    @Test
    public void testGameStartWithTwoPlayers() throws Exception {

        AgentController master = container.createNewAgent(
                "GameMasterTest",
                "explodingkittens.remote.GameMasterAgent",
                new Object[]{"2"}
        );
        master.start();
        Thread.sleep(1000);

        AgentController player1 = container.createNewAgent(
                "Giocatore1",
                "explodingkittens.remote.TestPlayerAgent",
                new Object[]{"ACTIVE"}
        );
        player1.start();
        Thread.sleep(500);

        AgentController player2 = container.createNewAgent(
                "Giocatore2",
                "explodingkittens.remote.TestPlayerAgent",
                new Object[]{"ACTIVE"}
        );
        player2.start();

        TestPlayerAgent instance1 = null, instance2 = null;
        long deadline = System.currentTimeMillis() + 5000;
        while ((instance1 == null || instance2 == null) && System.currentTimeMillis() < deadline) {
            instance1 = TestPlayerAgent.getInstance("Giocatore1");
            instance2 = TestPlayerAgent.getInstance("Giocatore2");
            Thread.sleep(100);
        }

        assertNotNull(instance1, "Giocatore1 non trovato nel registry");
        assertNotNull(instance2, "Giocatore2 non trovato nel registry");

        boolean game1Started = instance1.waitFor(instance1::isGameStarted, 10000);
        boolean game2Started = instance2.waitFor(instance2::isGameStarted, 10000);

        assertTrue(game1Started, "Giocatore1 non ha ricevuto HAND_INIT");
        assertTrue(game2Started, "Giocatore2 non ha ricevuto HAND_INIT");
    }

}

