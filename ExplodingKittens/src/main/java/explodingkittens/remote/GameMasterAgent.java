package explodingkittens.remote;

import explodingkittens.game.model.*;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.Map;

/**
 * GameMasterAgent è l'agente primario che gestisce lo stato del gioco.
 * Tutta la logica di gioco condivisa è nella classe base AbstractMasterAgent.
 * Qui viene gestito solo: registrazione DF, lobby, heartbeat verso il backup.
 * Implementa 4 behaviours:
 * 1) Gestione lobby, attende il join dei giocatori.
 * 2) Distribuzione carte e avvio della partita.
 * 3) Gestione logica di gioco (contenuta in AbstractMasterAgent)
 * 4) Monitoraggio heartbeat client.
 */
public class GameMasterAgent extends AbstractMasterAgent {
    private int expectedPlayers = -1;
    private AID backupMasterAID;

    /**
     * Metodo di inizializzazione dell'agente.
     * - Registra il servizio nel DF
     * - Inizializza stato di gioco e mazzo
     * - Avvia heartbeat verso il backup
     * - Avvia il behaviour di attesa giocatori
     */
    @Override
    protected void setup() {
        registerInDF();
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            expectedPlayers = Integer.parseInt(args[0].toString());
        }
        gameState = new GameState();
        deck      = new Deck();

        startHeartbeat();
        System.out.println("GameMaster avviato, aspetto " + expectedPlayers + " giocatori...");
        addBehaviour(new WaitForPlayersBehaviour());
    }

    /**
     * Metodo chiamato alla terminazione dell'agente.
     * Effettua la deregistrazione dal Directory Facilitator.
     */
    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (Exception ignored) {}
    }

    /**
     * Registra l'agente nel Directory Facilitator (DF)
     * come servizio di tipo "game-master".
     */
    private void registerInDF() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("game-master");
            sd.setName("exploding-kittens");
            dfd.addServices(sd);
            DFService.register(this, dfd);
            System.out.println("GameMaster registrato nel DF.");
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Behaviour 1
     */
    private class WaitForPlayersBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String content = msg.getContent();
                if (content.startsWith(Messages.NICKNAME_AND_LOBBY_CHECK)) {
                    String requestedNick = content.substring(Messages.NICKNAME_AND_LOBBY_CHECK.length()).trim();

                    for(Player player : gameState.getActivePlayers()){
                        System.out.println("Nickname in GameMaster:" + player.getNickname());
                    }
                    boolean alreadyUsed = gameState.getActivePlayers().stream()
                            .anyMatch(p -> p.getNickname()
                                    .equalsIgnoreCase(requestedNick));

                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    if (alreadyUsed) {
                        reply.setContent(Messages.INVALID_NICKNAME);
                    }
                    else if (expectedPlayers != -1 && gameState.getActivePlayers().size() >= expectedPlayers) {
                        reply.setContent(Messages.LOBBY_FULL);
                    }
                    else {
                        reply.setContent(expectedPlayers == -1 ? Messages.VALID_HOST : Messages.VALID_GUEST);
                    }
                    send(reply);
                    return;
                }
                if (content != null && content.startsWith(Messages.JOIN)) {
                    if (expectedPlayers == -1) {
                        try {
                            String[] parts = content.split(":");
                            expectedPlayers = parts.length > 1
                                    ? Integer.parseInt(parts[1])
                                    : 2;
                            System.out.println("Lobby creata con limite: " + expectedPlayers);
                        } catch (NumberFormatException e) {
                            expectedPlayers = 2;
                        }
                    }

                    if (gameState.getActivePlayers().size() < expectedPlayers) {
                        String playerName = msg.getSender().getName();
                        if (findPlayerByAgentName(playerName) == null) {
                            Player player = new Player(playerName, msg.getSender().getLocalName());
                            gameState.addPlayer(player);
                            sincronize();

                            System.out.println("Registrato: " + playerName
                                    + " (" + gameState.getActivePlayers().size() + "/" + expectedPlayers + ")");

                            ACLMessage reply = msg.createReply();
                            reply.setPerformative(ACLMessage.CONFIRM);
                            reply.setContent(Messages.JOINED);
                            send(reply);

                            if (gameState.getActivePlayers().size() == expectedPlayers) {
                                myAgent.removeBehaviour(this);
                                addBehaviour(new StartGameBehaviour());
                            }
                        }
                    } else {
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.REFUSE);
                        reply.setContent(Messages.LOBBY_FULL);
                        send(reply);
                    }
                }
            } else {
                block();
            }
        }
    }

    /**
     * Behaviour 2
     */
    private class StartGameBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            deck = DeckBuilder.prepareBaseDeck(expectedPlayers);
            Map<String, String> hands = DeckBuilder.buildPlayerHands(deck, gameState.getActivePlayers());
            DeckBuilder.insertExplodingKittens(deck, expectedPlayers);

            for (Player player : gameState.getActivePlayers()) {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(new AID(player.getAgentName(), true));
                msg.setContent(Messages.HAND_INIT + hands.get(player.getAgentName()));
                send(msg);
            }

            System.out.println("Partita avviata!");
            broadcastPlayersList();
            nextTurn();
            addBehaviour(new HandleActionBehaviour());
            startPlayerTimeoutChecker();
        }
    }

    /**
     * Behaviour 3
     */
    private class HandleActionBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg != null) {
                String content = msg.getContent();
                if (content.startsWith(Messages.JOIN) || content.startsWith(Messages.NICKNAME_AND_LOBBY_CHECK)) {
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent(Messages.LOBBY_FULL);
                    send(reply);
                    return;
                }
                processGameMessage(msg);
            } else {
                block();
            }
        }
    }

    /**
     * Behaviour 4
     */
    private void startPlayerTimeoutChecker() {
        addBehaviour(new TickerBehaviour(this, 2000) {
            @Override
            protected void onTick() {
                checkDisconnectedPlayers();
            }
        });
    }

    /**
     * Metodo per l'invio degli heartbeats al BackupMasterAgent.
     */
    private void startHeartbeat() {
        addBehaviour(new TickerBehaviour(this, 3000) {
            @Override
            protected void onTick() {
                if (backupMasterAID == null) backupMasterAID = findBackup();
                if (backupMasterAID != null) {
                    ACLMessage hb = new ACLMessage(ACLMessage.INFORM);
                    hb.addReceiver(backupMasterAID);
                    hb.setContent(Messages.HEARTBEAT + ":" + serializeState());
                    myAgent.send(hb);
                }
            }
        });
    }

    /**
     * Sincronizza manualmente lo stato con il BackupMasterAgent.
     * Utilizzato quando cambia lo stato (es. nuovo player).
     */
    private void sincronize() {
        if (backupMasterAID == null) backupMasterAID = findBackup();
        if (backupMasterAID != null) {
            ACLMessage hb = new ACLMessage(ACLMessage.INFORM);
            hb.addReceiver(backupMasterAID);
            hb.setContent(Messages.HEARTBEAT + ":" + serializeState());
            send(hb);
        }
    }

    /**
     * Cerca nel Directory Facilitator un agente di tipo "backup-master".
     * @return AID del BackupMasterAgent se trovato, null altrimenti
     */
    private AID findBackup() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("backup-master");
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) return result[0].getName();
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }
}