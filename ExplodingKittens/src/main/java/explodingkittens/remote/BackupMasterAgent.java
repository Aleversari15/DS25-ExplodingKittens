package explodingkittens.remote;

import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

/**
 * Agente master di backup. Resta in ascolto degli heartbeat dal GameMaster primario.
 * Se il primario non risponde entro TIMEOUT ms, si promuove a nuovo GameMaster,
 * notifica i player e riprende la partita dallo stato ricevuto via heartbeat.
 * Tutta la logica condivisa è in AbstractMasterAgent.
 */
public class BackupMasterAgent extends AbstractMasterAgent {

    private long lastHeartbeatTime = 0;
    private boolean promoted = false;

    private static final long TIMEOUT = 10_000;
    private static final long TICKER_PERIOD = 2_000;

    @Override
    protected void setup() {
        registerInDF();
        gameState = new explodingkittens.game.model.GameState();
        deck = new explodingkittens.game.model.Deck();
        addBehaviour(new MainBehaviour());
        addBehaviour(new TimeoutWatchBehaviour(this, TICKER_PERIOD));
        System.out.println("[BackupMaster] Avviato e pronto al subentro.");
    }

    private class MainBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg == null) { block(); return; }

            String senderLocal = msg.getSender().getLocalName();
            if (senderLocal.equals("ams") || senderLocal.equals("df")) return;

            String content = msg.getContent();
            if (content == null) return;

            if (!promoted) {
                if (content.startsWith(Messages.HEARTBEAT)) {
                    lastHeartbeatTime = System.currentTimeMillis();
                    reconstructState(content.substring(Messages.HEARTBEAT.length() + 1));
                }
            } else {
                if (content.startsWith(Messages.NICKNAME_AND_LOBBY_CHECK)) {
                    handleNicknameCheck(msg, content);
                } else if (content.startsWith(Messages.JOIN)) {
                    handleJoin(msg, content);
                } else {
                    processGameMessage(msg);
                }
            }
        }
    }

    private class TimeoutWatchBehaviour extends TickerBehaviour {
        TimeoutWatchBehaviour(jade.core.Agent a, long period) { super(a, period); }

        @Override
        protected void onTick() {
            if (!promoted
                    && lastHeartbeatTime > 0
                    && System.currentTimeMillis() - lastHeartbeatTime > TIMEOUT) {
                promoteToMaster();
                stop();
            }
        }
    }

    private void promoteToMaster() {
        System.out.println("[BackupMaster] GameMaster primario caduto. Mi promuovo a Master!");
        promoted = true;

        updateDFtoPrimary();
        broadcastNewMaster();

        if (gameState != null
                && !gameState.isGameOver()
                && !gameState.getActivePlayers().isEmpty()
                && gameStarted) {
            nextTurn();
        } else {
            System.out.println("[BackupMaster] Stato non pronto, nextTurn rimandato."
                    + " Giocatori=" + (gameState != null ? gameState.getActivePlayers().size() : 0)
                    + " gameStarted=" + gameStarted);
        }

        startPlayerTimeoutChecker();
    }

    private void registerInDF() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("backup-master");
            sd.setName("exploding-kittens-backup");
            dfd.addServices(sd);
            DFService.register(this, dfd);
            System.out.println("[BackupMaster] Registrato nel DF.");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void updateDFtoPrimary() {
        try {
            DFService.deregister(this);
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("game-master");
            sd.setName("exploding-kittens-promoted");
            dfd.addServices(sd);
            DFService.register(this, dfd);
            System.out.println("[BackupMaster] Ora registrato come GameMaster nel DF.");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void broadcastNewMaster() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("player");
        template.addServices(sd);
        try {
            DFAgentDescription[] results = DFService.search(this, template);
            for (DFAgentDescription dfd : results) {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(dfd.getName());
                msg.setContent(Messages.NEW_MASTER);
                send(msg);
            }
            System.out.println("[BackupMaster] NEW_MASTER inviato a " + results.length + " giocatori.");
        } catch (Exception e) { e.printStackTrace(); }
    }
}