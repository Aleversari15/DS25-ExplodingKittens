package explodingkittens.remote;

import jade.core.*;
import jade.core.Runtime;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Classe di test d'integrazione per la verifica della Fault Tolerance del sistema.
 * Valida il meccanismo di failover, assicurando che in caso di crash
 * del {@code GameMasterAgent}, il {@code BackupMasterAgent} subentri correttamente,
 * notifichi i giocatori esistenti e sia in grado di accettare nuove connessioni.
 */
public class FaultTolleranceTest {

    private Runtime rt;
    private AgentContainer container;

    @BeforeEach
    void setUp() {
        rt = Runtime.instance();
        Profile p = new ProfileImpl();
        container = rt.createMainContainer(p);
    }

    @AfterEach
    void tearDown() {
        try {
            if (container != null)
                container.kill();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * Verifica che il BackupMasterAgent notifichi i player già registrati
     * quando assume il ruolo di Master a seguito di un crash.
     * Flusso del test:
     * 1. Avvio di GameMaster e BackupMaster.
     * 2. Un player esegue il JOIN con successo sul GameMaster.
     * 3. Kill del GameMaster.
     * 4. Verifica che il BackupMaster invii Messages.NEW_MASTER al player.
     *  @throws Exception se si verificano errori nella creazione degli agenti.
     */
    @Test
    public void testNewMasterNotificationAfterFailover() throws Exception {

        AgentController gameMaster = container.createNewAgent(
                "GameMaster",
                "explodingkittens.remote.GameMasterAgent",
                new Object[]{"2"}
        );
        gameMaster.start();

        AgentController backupMaster = container.createNewAgent(
                "BackupMaster",
                "explodingkittens.remote.BackupMasterAgent",
                null
        );
        backupMaster.start();

        Thread.sleep(5000); // Attendiamo che GameMaster e BackupMaster si inizializzino

        final BlockingQueue<ACLMessage> messageQueue = new LinkedBlockingQueue<>();

        Agent testerAgent = new Agent() {
            @Override
            protected void setup() {

                try {
                    DFAgentDescription dfd = new DFAgentDescription();
                    dfd.setName(getAID());
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("player");
                    sd.setName("test-player");
                    dfd.addServices(sd);
                    DFService.register(this, dfd);
                    System.out.println("[Player] Registrato nel DF come player");
                } catch (Exception e) {
                    e.printStackTrace();
                }

                ACLMessage joinMsg = new ACLMessage(ACLMessage.REQUEST);
                joinMsg.addReceiver(new AID("GameMaster", AID.ISLOCALNAME));
                joinMsg.setContent(Messages.JOIN + ":2");
                send(joinMsg);

                System.out.println("[Player] Inviato JOIN al GameMaster");


                addBehaviour(new CyclicBehaviour() {
                    @Override
                    public void action() {
                        ACLMessage msg = receive();
                        if (msg != null) {
                            System.out.println("[Player] Messaggio ricevuto: performative="
                                    + ACLMessage.getPerformative(msg.getPerformative())
                                    + ", content=" + msg.getContent()
                                    + ", sender=" + msg.getSender().getLocalName());
                            messageQueue.add(msg);
                        } else {
                            block();
                        }
                    }
                });
            }
        };

        AgentController player = container.acceptNewAgent("Player", testerAgent);
        player.start();

        ACLMessage joinResponse = messageQueue.poll(5, TimeUnit.SECONDS);
        assertNotNull(joinResponse, "Il GameMaster dovrebbe rispondere alla richiesta di JOIN");
        assertEquals(ACLMessage.CONFIRM, joinResponse.getPerformative(), "Il GameMaster dovrebbe confermare il JOIN");
        assertEquals(Messages.JOINED, joinResponse.getContent(), "Il contenuto della risposta dovrebbe essere JOINED");

        Thread.sleep(12000);
        gameMaster.kill();

        ACLMessage newMasterNotification = messageQueue.poll(20, TimeUnit.SECONDS);

        assertNotNull(newMasterNotification, "Il BackupMaster dovrebbe inviare una notifica NEW_MASTER ai player registrati");
        assertEquals(ACLMessage.INFORM, newMasterNotification.getPerformative(), "La notifica NEW_MASTER dovrebbe avere performativa INFORM");
        assertEquals(Messages.NEW_MASTER, newMasterNotification.getContent(), "Il contenuto della notifica dovrebbe essere NEW_MASTER");
        assertEquals("BackupMaster", newMasterNotification.getSender().getLocalName(), "Il mittente dovrebbe essere il BackupMaster");
    }
    /**
     * Verifica che il BackupMasterAgent, una volta promosso, sia in grado
     * di accettare nuove richieste di JOIN da parte di player che si connettono tardi.
     * Flusso del test:
     * 1. Avvio dei Master.
     * 2. Crash del GameMaster primario.
     * 3. Attesa del periodo di promozione.
     * 4. Un nuovo player tenta il JOIN puntando direttamente al BackupMaster.
     * 5. Verifica che il BackupMaster risponda positivamente.
     * * @throws Exception se si verificano errori nella creazione degli agenti.
     */
    @Test
    public void testJoinAfterFailover() throws Exception {

        AgentController gameMaster = container.createNewAgent("GameMaster", "explodingkittens.remote.GameMasterAgent", new Object[]{"2"});
        gameMaster.start();

        AgentController backupMaster = container.createNewAgent("BackupMaster", "explodingkittens.remote.BackupMasterAgent", null);
        backupMaster.start();

        Thread.sleep(5000);

        gameMaster.kill();

        Thread.sleep(15000);

        final BlockingQueue<ACLMessage> messageQueue = new LinkedBlockingQueue<>();

        Agent testerAgent = new Agent() {
            @Override
            protected void setup() {

                try {
                    DFAgentDescription dfd = new DFAgentDescription();
                    dfd.setName(getAID());
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("player");
                    sd.setName("test-player-late");
                    dfd.addServices(sd);
                    DFService.register(this, dfd);
                    System.out.println("[Player] Registrato nel DF come player");
                } catch (Exception e) {
                    e.printStackTrace();
                }

                ACLMessage joinMsg = new ACLMessage(ACLMessage.REQUEST);
                joinMsg.addReceiver(new AID("BackupMaster", AID.ISLOCALNAME));
                joinMsg.setContent(Messages.JOIN + ":2");
                send(joinMsg);
                System.out.println("[Player] Inviato JOIN al BackupMaster (post-failover)");

                addBehaviour(new CyclicBehaviour() {
                    @Override
                    public void action() {
                        ACLMessage msg = receive();
                        if (msg != null) {
                            System.out.println("[Player] Messaggio ricevuto: performative="
                                    + ACLMessage.getPerformative(msg.getPerformative())
                                    + ", content=" + msg.getContent()
                                    + ", sender=" + msg.getSender().getLocalName());
                            messageQueue.add(msg);
                        } else {
                            block();
                        }
                    }
                });
            }
        };

        AgentController player = container.acceptNewAgent("PlayerLate", testerAgent);
        player.start();

        ACLMessage joinResponse = messageQueue.poll(10, TimeUnit.SECONDS);

        assertNotNull(joinResponse, "Il BackupMaster (promosso) dovrebbe rispondere alla richiesta di JOIN");
        assertEquals(ACLMessage.CONFIRM, joinResponse.getPerformative(), "Il BackupMaster dovrebbe confermare il JOIN con CONFIRM");
        assertEquals(Messages.JOINED, joinResponse.getContent(), "Il contenuto della risposta dovrebbe essere JOINED");
        assertEquals("BackupMaster", joinResponse.getSender().getLocalName(), "Il mittente della risposta dovrebbe essere il BackupMaster");

    }
/*
    @Test
    public void testHeartbeatExchange() throws Exception {
        AgentController gameMaster = container.createNewAgent("GameMaster", "explodingkittens.remote.GameMasterAgent", new Object[]{"2"});
        gameMaster.start();

        final BlockingQueue<ACLMessage> heartbeatMessageQueue = new LinkedBlockingQueue<>();

        Agent testerBackupAgent = new Agent(){
            @Override
            protected void setup(){
                try {
                    DFAgentDescription dfd = new DFAgentDescription();
                    dfd.setName(getAID());
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("backup-master");
                    sd.setName("exploding-kittens-backup");
                    dfd.addServices(sd);
                    DFService.register(this, dfd);
                    System.out.println("[SpyBackup] Registrato nel DF come backup-master");
                } catch (Exception e) {
                    e.printStackTrace();
                }


                // Rimane in ascolto e raccoglie tutti i messaggi di heartbeat
                addBehaviour(new CyclicBehaviour() {
                    @Override
                    public void action() {
                        ACLMessage msg = receive();
                        if (msg != null) {
                            System.out.println("[SpyBackup] Messaggio ricevuto: "
                                    + "performative=" + ACLMessage.getPerformative(msg.getPerformative())
                                    + ", content=" + msg.getContent().substring(0, Math.min(50, msg.getContent().length())) + "..."
                                    + ", sender=" + msg.getSender().getLocalName());
                            if (msg.getContent().startsWith(Messages.HEARTBEAT)) {
                                heartbeatMessageQueue.add(msg);
                            }
                        } else {
                            block();
                        }
                    }
                });
            }
        };

        AgentController spy = container.acceptNewAgent("BackupMaster", spyAgent);
        spy.start();

        ACLMessage firstHeartbeat = heartbeatQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(firstHeartbeat,
                "Il GameMaster dovrebbe inviare almeno un heartbeat al BackupMaster");
        assertEquals(ACLMessage.INFORM, firstHeartbeat.getPerformative(),
                "L'heartbeat dovrebbe avere performativa INFORM");
        assertTrue(firstHeartbeat.getContent().startsWith(Messages.HEARTBEAT),
                "Il contenuto dovrebbe iniziare con HEARTBEAT");
        assertEquals("GameMaster", firstHeartbeat.getSender().getLocalName(),
                "Il mittente dell'heartbeat dovrebbe essere il GameMaster");
    }*/
}