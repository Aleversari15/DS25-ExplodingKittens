package explodingkittens.remote;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agente di test che simula un PlayerAgent semplificato.
 * Supporta due modalità:
 *  -"ACTIVE": si connette e manda heartbeat per tutta la durata del test
 *  -"DISCONNECT": si connette, manda heartbeat per 2 secondi poi smette (simula disconnessione)
 * Espone il proprio stato tramite un registry statico (nome agente, istanza) accessibile dal test.
 */
public class TestPlayerAgent extends Agent {
    private static final Map<String, TestPlayerAgent> REGISTRY = new ConcurrentHashMap<>();
    private AID   gameMasterAID;
    private String mode;
    private volatile boolean winnerReceived        = false;
    private volatile boolean disconnectedNotified  = false;
    private volatile boolean gameStarted           = false;
    private volatile boolean joinConfirmed         = false;

    public static TestPlayerAgent getInstance(String localName) {
        return REGISTRY.get(localName);
    }

    @Override
    protected void setup() {
        REGISTRY.put(getLocalName(), this);

        Object[] args = getArguments();
        mode = (args != null && args.length > 0) ? args[0].toString() : "ACTIVE";

        System.out.println("[TestPlayerAgent] " + getLocalName() + " avviato in modalità: " + mode);

        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                while (gameMasterAID == null) {
                    gameMasterAID = findGameMaster();
                    if (gameMasterAID == null) {
                        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                    }
                }

                ACLMessage join = new ACLMessage(ACLMessage.REQUEST);
                join.addReceiver(gameMasterAID);
                join.setContent(Messages.JOIN + ":2");
                send(join);

                System.out.println("[TestPlayerAgent] " + getLocalName() + " JOIN inviato.");
            }
        });

        // Heartbeat: il DISCONNECT smette dopo 2 secondi
        if (mode.equals("ACTIVE")) {
            addBehaviour(new TickerBehaviour(this, 1000) {
                @Override
                protected void onTick() {
                    if (gameMasterAID != null) {
                        ACLMessage hb = new ACLMessage(ACLMessage.INFORM);
                        hb.addReceiver(gameMasterAID);
                        hb.setContent(Messages.HEARTBEAT_CLIENT);
                        send(hb);
                    }
                }
            });
        } else {
            addBehaviour(new TickerBehaviour(this, 500) {
                private int count = 0;
                @Override
                protected void onTick() {
                    if (gameMasterAID != null && count < 4) {
                        ACLMessage hb = new ACLMessage(ACLMessage.INFORM);
                        hb.addReceiver(gameMasterAID);
                        hb.setContent(Messages.HEARTBEAT_CLIENT);
                        send(hb);
                        count++;
                    } else if (count >= 4) {
                        System.out.println("[TestPlayerAgent] " + getLocalName() + " smette di mandare heartbeat (simula disconnessione).");
                        stop();
                    }
                }
            });
        }

        // Listener messaggi dal GameMaster
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    String content = msg.getContent();
                    System.out.println("[TestPlayerAgent] " + getLocalName() + " ricevuto: " + content);

                    if (content.startsWith(Messages.HAND_INIT)) {
                        gameStarted = true;
                    }
                    if (content.startsWith(Messages.WINNER)) {
                        winnerReceived = true;
                        System.out.println("[TestPlayerAgent] " + getLocalName() + " WINNER ricevuto!");
                    }
                    if (content.startsWith(Messages.PLAYER_DISCONNECTED)) {
                        disconnectedNotified = true;
                    }
                    if (content.equals(Messages.JOINED)) {
                        joinConfirmed = true;
                        System.out.println("[TestPlayerAgent] " + getLocalName() + " JOIN confermato!");
                    }
                } else {
                    block();
                }
            }
        });
    }

    @Override
    protected void takeDown() {
        REGISTRY.remove(getLocalName());
    }

    /**
     * True se questo agente ha ricevuto il messaggio WINNER.
     * */
    public boolean hasWon() { return winnerReceived; }

    /**
     * True se questo agente è stato notificato della disconnessione di un altro player.
     * */
    public boolean wasNotifiedOfDisconnection() { return disconnectedNotified; }

    /**
     * True se la partita è iniziata (HAND_INIT ricevuto).
     * */
    public boolean isGameStarted() { return gameStarted; }

    public boolean isJoinConfirmed() { return joinConfirmed; }


    private AID findGameMaster() {
        try {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("game-master");
            template.addServices(sd);
            DFAgentDescription[] result = DFService.search(this, template);
            return (result.length > 0) ? result[0].getName() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Attende fino a che la condizione diventa true o scade il timeout (ms).
     * */
    public boolean waitFor(java.util.function.BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) return false;
            Thread.sleep(200);
        }
        return true;
    }
}