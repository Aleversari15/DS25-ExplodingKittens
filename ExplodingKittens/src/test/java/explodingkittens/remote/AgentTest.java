package explodingkittens.remote;

import jade.core.AID;
import jade.core.Agent;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class AgentTest {

    private Runtime rt;
    private AgentContainer container;

    // Per testare in modo asincrono la ricezione dei messaggi da parte dell'agente tester
    public static CountDownLatch messageLatch;
    public static List<String> receivedMessages;

    @BeforeEach
    void setUp() {
        rt = Runtime.instance();
        // reset del Runtime per evitare che thread residui blocchino il nuovo container
        rt.setCloseVM(false);

        Profile p = new ProfileImpl();
        // porta casuale per evitare conflitti con altri test o istanze di JADE
        p.setParameter(Profile.MAIN_PORT, String.valueOf(1099 + (int)(Math.random() * 100)));
        container = rt.createMainContainer(p);

        receivedMessages = Collections.synchronizedList(new ArrayList<>());
    }

    @AfterEach
    void tearDown() {
        try {
            if (container != null) {
                container.kill();
                // Piccola attesa per permettere al sistema operativo di liberare la porta
                Thread.sleep(500);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Agente fittizio che simula un Player per testare il flusso dei messaggi scambiati col GameMaster.
     */
    public static class DummyPlayerTesterAgent extends Agent {
        @Override
        protected void setup() {
            // Invio della prima richiesta di join
            addBehaviour(new OneShotBehaviour() {
                @Override
                public void action() {
                    ACLMessage joinMsg = new ACLMessage(ACLMessage.REQUEST);
                    joinMsg.addReceiver(new AID("GameMasterTest", AID.ISLOCALNAME));
                    joinMsg.setContent(Messages.JOIN + ":2");
                    send(joinMsg);
                }
            });

            // Ascolto delle risposte
            addBehaviour(new CyclicBehaviour() {
                @Override
                public void action() {
                    ACLMessage msg = receive();
                    if (msg != null) {
                        receivedMessages.add(msg.getContent());
                        if (messageLatch != null) {
                            messageLatch.countDown();
                        }
                    } else {
                        block();
                    }
                }
            });
        }
    }

    @Test
    @DisplayName("Test di Join: Il Master deve confermare la registrazione e scambiare il messaggio di JOINED")
    public void testPlayerJoinCommunication() throws Exception {
        // Prepariamo l'attesa per 1 messaggio (il JOINED)
        messageLatch = new CountDownLatch(1);

        AgentController master = container.createNewAgent(
                "GameMasterTest",
                "explodingkittens.remote.GameMasterAgent",
                new Object[]{"2"} // il gameMaster aspetta 2 giocatori
        );
        master.start();

        // Aspettiamo che il Master si registri
        Thread.sleep(1000);

        // Crea un Dummy Player che si unisce al Master e invia un messaggio di JOIN reale
        AgentController dummyPlayer = container.createNewAgent(
                "GiocatoreTester",
                DummyPlayerTesterAgent.class.getName(),
                new Object[]{}
        );
        dummyPlayer.start();

        // Attendiamo asincronamente la ricezione del messaggio (timeout per non bloccare i test)
        boolean received = messageLatch.await(5, TimeUnit.SECONDS);

        assertTrue(received, "L'agente non ha ricevuto nessuna risposta in tempo");
        assertTrue(receivedMessages.contains(Messages.JOINED), "Il messaggio di JOINED non  stato scambiato con successo");
    }

    @Test
    @DisplayName("Test di Partita: Simula l'avvio di una partita con 2 giocatori e verifica lo scambio dei messaggi iniziali")
    public void testGameStartWithTwoPlayers() throws Exception {
        messageLatch = new CountDownLatch(2);

        AgentController master = container.createNewAgent(
                "GameMasterTest",
                "explodingkittens.remote.GameMasterAgent",
                new Object[]{"2"}
        );
        master.start();

        Thread.sleep(1000);

        // primo giocatore (DummyPlayerTesterAgent)
        AgentController player1 = container.createNewAgent(
                "GiocatoreTester1",
                DummyPlayerTesterAgent.class.getName(),
                new Object[]{}
        );
        player1.start();

        Thread.sleep(1000);

        // secondo giocatore (PlayerAgent)
        AgentController player2 = container.createNewAgent(
                "GiocatoreReale2",
                "explodingkittens.remote.PlayerAgent",
                new Object[]{"Giocatore 2", 2}
        );
        player2.start();

        // Aspettiamo la ricezione di multipli messaggi
        messageLatch.await(5, TimeUnit.SECONDS);

        assertNotNull(master.getState(), "Lo stato del Master non dovrebbe essere nullo");
        assertTrue(receivedMessages.contains(Messages.JOINED), "Il master deve aver risposto con il JOINED al test player");

        // Verifica la ricezione della mano iniziale o dei turni inizializzati da parte dello scambrio start partita
        boolean hasCardMessages = false;
        long turnMessages = 0;
        for (String msg : receivedMessages) {
            if (msg.startsWith(Messages.ADD_CARD)) hasCardMessages = true;
            if (msg.contains("TURN")) turnMessages++;
        }
        assertTrue(hasCardMessages || turnMessages > 0, "Durante l'avvio della partita dovrebbero essere stati scambiati messaggi sulle carte o sui turni");
    }

}

