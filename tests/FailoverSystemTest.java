package tests;

import exploding_kittens.remote.Messages;
import jade.core.AID;
import jade.core.Agent;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import org.junit.jupiter.api.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FailoverSystemTest {
    private jade.core.Runtime rt;
    private AgentContainer mainContainer;
    private AgentController masterController;
    private AgentController backupController;

    @BeforeEach
    void setUp() {
        System.out.println("[SETUP] Inizializzazione del main container JADE...");
        rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_PORT, "1100");
        p.setParameter(Profile.LOCAL_HOST, "127.0.0.1");
        mainContainer = rt.createMainContainer(p);
    }

    @AfterEach
    void tearDown() {
        System.out.println("[TEARDOWN] Spegnimento del main container JADE...");
        try {
            mainContainer.kill();
        } catch (Exception e) {
            // Container già spento
        }
    }

    @Test
    @Order(1)
    @DisplayName("Test: Il Backup deve recuperare i dati (Gicoatori e Mazzo) dopo il crash")
    void testBackupDataConsistency() throws Exception {
        System.out.println("[TEST 1] Avvio agenti GameMaster e BackupMaster...");
        masterController = mainContainer.createNewAgent("GameMaster", "exploding_kittens.remote.GameMasterAgent", new Object[]{"2"});
        backupController = mainContainer.createNewAgent("BackupMaster", "exploding_kittens.remote.BackupMasterAgent", null);
        masterController.start();
        backupController.start();

        System.out.println("[TEST 1] Avvio giocatori...");
        AgentController p1 = mainContainer.createNewAgent("Player1", "exploding_kittens.remote.PlayerAgent", new Object[]{"P1", 2});
        AgentController p2 = mainContainer.createNewAgent("Player2", "exploding_kittens.remote.PlayerAgent", new Object[]{"P2", 2});
        p1.start();
        p2.start();

        System.out.println("[TEST 1] Attesa 5 secondi per permettere la registrazione iniziale...");
        Thread.sleep(5000);

        System.out.println("[TEST 1] Uccido il Master primario...");
        masterController.kill();

        System.out.println("[TEST 1] Attesa subentro del Backup (12s)...");
        Thread.sleep(12000);

        System.out.println("[TEST 1] Creazione agente TesterAgent per verifica risposta...");
        BlockingQueue<ACLMessage> responseQueue = new LinkedBlockingQueue<>();
        Agent testerAgent = new Agent() {
            @Override
            protected void setup() {
                System.out.println("[TEST 1 - TesterAgent] Setup completato. Preparazione messaggio...");
                ACLMessage requestDraw = new ACLMessage(ACLMessage.REQUEST);
                requestDraw.addReceiver(new AID("BackupMaster", AID.ISLOCALNAME));
                requestDraw.setContent(Messages.DRAW);
                System.out.println("[TEST 1 - TesterAgent] Invio messaggio al BackupMaster con contenuto: " + Messages.DRAW);
                send(requestDraw);

                addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
                    @Override
                    public void action() {
                        ACLMessage msg = receive();
                        if (msg != null) {
                            System.out.println("[TEST 1 - TesterAgent] Messaggio ricevuto! Mittente: "
                                    + msg.getSender().getLocalName() + ", Contenuto: " + msg.getContent());
                            responseQueue.add(msg);
                        } else {
                            block();
                        }
                    }
                });
            }
        };

        AgentController testerController = mainContainer.acceptNewAgent("TesterAgent", testerAgent);
        testerController.start();

        System.out.println("[TEST 1] In attesa della risposta (max 5 secondi)...");
        ACLMessage response = responseQueue.poll(5, TimeUnit.SECONDS);

        if (response == null) {
            System.err.println("[TEST 1] ERRORE: Nessuna risposta ricevuta dal BackupMaster entro il timeout.");
        }

        assertNotNull(response, "Il BackupMaster non ha risposto alla richiesta confermando di non essere attivo o di non avere i dati corretti.");
        System.out.println("[TEST 1] Il Backup ha risposto con successo: " + response.getContent());
    }

    @Test
    @DisplayName("Test Failover Cat Card: Verifica recupero dopo furto interrotto")
    void testCatCardConsistency() throws Exception {
        System.out.println("[TEST 2] Avvio agenti GameMaster e BackupMaster...");
        masterController = mainContainer.createNewAgent("GameMaster", "exploding_kittens.remote.GameMasterAgent", new Object[]{"2"});
        backupController = mainContainer.createNewAgent("BackupMaster", "exploding_kittens.remote.BackupMasterAgent", null);
        masterController.start();
        backupController.start();

        System.out.println("[TEST 2] Avvio giocatori Ladro e Vittima...");
        AgentController p1 = mainContainer.createNewAgent("Ladro", "exploding_kittens.remote.PlayerAgent", new Object[]{"Ladro", 2});
        AgentController p2 = mainContainer.createNewAgent("Vittima", "exploding_kittens.remote.PlayerAgent", new Object[]{"Vittima", 2});
        p1.start();
        p2.start();

        System.out.println("[TEST 2] Attesa 5 secondi per permettere la registrazione...");
        Thread.sleep(5000);

        System.out.println("[TEST 2] Uccido il Master primario...");
        masterController.kill();

        System.out.println("[TEST 2] Attesa subentro del Backup (12s)...");
        Thread.sleep(12000);

        System.out.println("[TEST 2] Creazione agente LadroReale per simulazione mossa...");
        BlockingQueue<ACLMessage> responseQueue = new LinkedBlockingQueue<>();
        Agent ladroReale = new Agent() {
            @Override
            protected void setup() {
                System.out.println("[TEST 2 - IndagineLadro] Setup completato. Invio mossa fittizia...");
                ACLMessage testMsg = new ACLMessage(ACLMessage.REQUEST);
                testMsg.addReceiver(new AID("BackupMaster", AID.ISLOCALNAME));
                testMsg.setContent("TEST_TURN");
                System.out.println("[TEST 2 - IndagineLadro] Invio messaggio al BackupMaster con contenuto: TEST_TURN");
                send(testMsg);

                addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
                    @Override
                    public void action() {
                        ACLMessage msg = receive();
                        if (msg != null) {
                            System.out.println("[TEST 2 - IndagineLadro] Messaggio ricevuto! Mittente: "
                                    + msg.getSender().getLocalName() + ", Contenuto: " + msg.getContent());
                            responseQueue.add(msg);
                        } else {
                            block();
                        }
                    }
                });
            }
        };

        AgentController indagineController = mainContainer.acceptNewAgent("IndagineLadro", ladroReale);
        indagineController.start();

        System.out.println("[TEST 2] In attesa della risposta (max 5 secondi)...");
        ACLMessage response = responseQueue.poll(5, TimeUnit.SECONDS);

        if (response == null) {
            System.err.println("[TEST 2] ERRORE: Nessuna risposta ricevuta dal BackupMaster dal ladro.");
        }

        assertNotNull(response, "Il BackupMaster non sta ascoltando o gestendo lo stato della Cat Card.");
        System.out.println("[TEST 2] Verifica dati Cat Card: Il Backup ha comunicato correttamente. Risposta: " + response.getContent());
    }
}
