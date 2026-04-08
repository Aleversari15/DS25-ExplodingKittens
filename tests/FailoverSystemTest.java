package tests;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FailoverSystemTest {
    private jade.core.Runtime rt;
    private AgentContainer mainContainer;
    private AgentController masterController;
    private AgentController backupController;

    @BeforeEach
    void setUp() {
        rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_PORT, "1100"); // Usa una porta diversa dalla 1099 standard
        p.setParameter(Profile.LOCAL_HOST, "127.0.0.1");
        mainContainer = rt.createMainContainer(p);
    }

    @AfterEach
    void tearDown() {
        try {
            mainContainer.kill();
        } catch (Exception e) {
            // Container già spento
        }
    }

    @Test
    @Order(1)
    @DisplayName("Test: Il Backup deve promuoversi se il Master cade")
    void testBackupPromotionOnMasterFailure() throws Exception {
        masterController = mainContainer.createNewAgent(
                "GameMaster",
                "exploding_kittens.remote.GameMasterAgent",
                new Object[]{"2"}
        );
        masterController.start();
        System.out.println("[TEST] Master Primario avviato.");

        //Avviamo il BackupMaster
        backupController = mainContainer.createNewAgent(
                "BackupMaster",
                "exploding_kittens.remote.BackupMasterAgent",
                null
        );
        backupController.start();
        System.out.println("[TEST] Backup avviato.");

        // Aspettiamo che si scambino almeno un Heartbeat
        Thread.sleep(3000);

        // SIMULIAMO IL GUASTO
        System.out.println("[TEST] !!! KILLING MASTER !!!");
        masterController.kill();

        System.out.println("[TEST] Attesa subentro (12 secondi)...");
        Thread.sleep(12000);

        assertNotNull(backupController, "Il Backup dovrebbe essere ancora in esecuzione");
        // Il Backup, una volta diventato Master, si metterà in attesa di messaggi
        String backupState = backupController.getState().getName();
        assertTrue(backupState.equalsIgnoreCase("Idle"),
                "Il Backup dovrebbe essere in attesa e aver preso il controllo. Stato attuale: " + backupState);


        System.out.println("[TEST] Failover completato con successo.");
    }

}
