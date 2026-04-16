package exploding_kittens.remote;

import exploding_kittens.game.model.Player;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

/**
 * Il BackupMasterAgent rimane in ascolto degli heartbeat dal GameMaster primario.
 * Se il primario non risponde entro TIMEOUT ms, si promuove a nuovo GameMaster
 * e continua la partita dal punto in cui era rimasta.
 * Tutta la logica di gioco è ereditata da AbstractMasterAgent.
 * Qui viene gestito solo: heartbeat, timeout, promozione, registrazione DF.
 * Implementa 3 behaviours:
 * 1) Ricezione messaggi: in fase di backup processa solo heartbeat, in fase master tutta la logica di gioco.
 * 2) Controllo timeout dell'heartbeat.
 * 3)
 */
public class BackupMasterAgent extends AbstractMasterAgent {
    private long    lastHeartbeatTime = 0;
    private boolean promoted          = false;
    private static final long TIMEOUT       = 10_000;
    private static final long TICKER_PERIOD =  2_000;

    @Override
    protected void setup() {
        registerInDF();
        addBehaviour(new MainBehaviour());
        addBehaviour(new TimeoutWatchBehaviour(this, TICKER_PERIOD));
    }

    /**
     * Behaviour 1
     */
    private class MainBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg == null) { block(); return; }

            String content = msg.getContent();
            if (content == null) return;

            if (!promoted) {
                // Fase backup
                if (content.startsWith(Messages.HEARTBEAT)) {
                    lastHeartbeatTime = System.currentTimeMillis();
                    reconstructState(content.substring(Messages.HEARTBEAT.length() + 1));
                }
            } else {
                // Fase master
                if (content.startsWith(Messages.JOIN)) {
                    handleJoinAfterPromotion(msg, content);
                } else {
                    processGameMessage(msg);
                }
            }
        }
    }

    /**
     * Behaviour 2
     */
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

    /**
     * Metodo per promozione da backup a master: rispristina lo
     * stato della partita e da questo momento gestisce tutti i messaggi di logica di gioco.
     */
    private void promoteToMaster() {
        System.out.println("GameMaster primario caduto. Mi promuovo a Master!");
        promoted = true;

        updateDFtoPrimary();
        broadcastNewMaster();

        if (gameState != null && !gameState.isGameOver()
                && !gameState.getActivePlayers().isEmpty() &&gameStarted) {
            nextTurn();
        }

        addBehaviour(new TickerBehaviour(this, 5000) {
            @Override
            protected void onTick() {
                checkDisconnectedPlayers();
            }
        });
    }

    //TODO: Ricontrollare
    /**
     * Gestisce eventuali JOIN che arrivano dopo la promozione
     * (es. un giocatore che si riconnette).
     */
    private void handleJoinAfterPromotion(ACLMessage msg, String content) {
        String playerName = msg.getSender().getName();
        if (this.expectedPlayers <= 0) {
            try {
                String[] parts = content.split(":");
                if (parts.length > 1) {
                    this.expectedPlayers = Integer.parseInt(parts[1]);
                    System.out.println("[Backup] Impostato expectedPlayers a " + expectedPlayers + " dal messaggio JOIN");
                }
            } catch (Exception e) {
                this.expectedPlayers = 2;
            }
        }
        // -------------------------

        Player existing = findPlayerByAgentName(playerName);

        if (existing != null) {
            // Giocatore già noto (Riconnessione)
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent(Messages.JOINED);
            send(reply);

            if (gameStarted) nextTurn();
            return;
        }

        // Nuovo giocatore
        if (!gameStarted && gameState.getActivePlayers().size() < expectedPlayers) {
            Player newPlayer = new Player(playerName, msg.getSender().getLocalName());
            gameState.addPlayer(newPlayer);

            System.out.println("Registrato post-failover: " + playerName
                    + " (" + gameState.getActivePlayers().size() + "/" + expectedPlayers + ")");

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent(Messages.JOINED);
            send(reply);

            // Se con questo nuovo arrivo la lobby è piena, facciamo partire il gioco
            if (gameState.getActivePlayers().size() == expectedPlayers) {
                System.out.println("Lobby completata post-failover. Inizializzo partita...");
                setupAndStartGame();
            }
        } else {
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.REFUSE);
            reply.setContent(gameStarted ? "GAME_IN_PROGRESS" : "LOBBY_FULL");
            send(reply);
        }
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
            System.out.println("BackupMaster registrato nel DF.");
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
            System.out.println("BackupMaster ora registrato come GameMaster nel DF.");
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
            System.out.println("NEW_MASTER inviato a " + results.length + " giocatori.");
        } catch (Exception e) { e.printStackTrace(); }
    }
}