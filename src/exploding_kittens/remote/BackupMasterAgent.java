package exploding_kittens.remote;

import exploding_kittens.game.model.*;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;
import java.util.stream.Collectors;

public class BackupMasterAgent extends Agent {
    private GameState gameState;
    private long lastHeartbeatTime;
    private static final long TIMEOUT = 10000;
    private static final String CAT_LOG = "[GameMaster - CAT_CARD] ";
    private Deck deck;

    @Override
    protected void setup() {
        registerInDF();

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {

                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    String content = msg.getContent();
                    if(content != null && content.startsWith(Messages.HEARTBEAT)) {
                        System.out.println("DEBUG: Heartbeat ricevutoooo!");
                        lastHeartbeatTime = System.currentTimeMillis();
                        String stateData = msg.getContent().substring(Messages.HEARTBEAT.length() +1); //in pratica gli facciamo prendere tutta la stringa dopo i :
                        reconstructState(stateData);
                    }

                } else {
                    block();
                }
            }
        });

        addBehaviour(new TickerBehaviour(this, 2000) {
            @Override
            protected void onTick() {
                if (lastHeartbeatTime > 0 && (System.currentTimeMillis() - lastHeartbeatTime > TIMEOUT)) {
                    promoteToMaster();
                    stop(); // Ferma questo ticker
                }
            }
        });
    }

    private void reconstructState(String data) {
        try {
            System.out.println("DEBUG DATA RICEVUTI: " + data);
            String[] parts = data.split(":", 4); // Usiamo -1 per non ignorare campi vuoti
            if (parts.length < 4){
                System.err.println("Dati insufficienti! Parti trovate: " + parts.length);
                return;
            }

            if (this.gameState == null) this.gameState = new GameState();
            if (this.deck == null) this.deck = new Deck();

            // 1. Ripristino Turni e Indice
            int currentIndex = Integer.parseInt(parts[0]);
            int turnsToPlay = Integer.parseInt(parts[1]);
            gameState.setCurrentPlayerIndex(currentIndex);
            gameState.setTurnsToPlay(turnsToPlay);

            // 2. Ripristino Mazzo
            List<Card> restoredCards = new ArrayList<>();
            if (!parts[2].isEmpty()) {
                for (String cardName : parts[2].split(",")) {
                    restoredCards.add(new Card(CardType.valueOf(cardName)));
                }
            }
            deck.setCards(restoredCards);

            // 3. Ripristino Giocatori
            List<Player> restoredPlayers = new ArrayList<>();
            if (!parts[3].isEmpty()) {
                for (String pData : parts[3].split("\\|")) {
                    String[] pParts = pData.split(",");
                    String agentAID = pParts[0];
                    String nickname = pParts[1];

                    if(nickname.startsWith("Player_")){
                        nickname = nickname.replaceFirst("Player_", "");
                    }
                    restoredPlayers.add(new Player(agentAID, nickname));
                }
            }
            gameState.setActivePlayers(restoredPlayers);
            System.out.println("DEBUG: Stato ricostruito. Giocatori: " + restoredPlayers.size());

        } catch (Exception e) {
            System.err.println("Errore durante la ricostruzione dello stato: " + e.getMessage());
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
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void promoteToMaster() {
        System.out.println("!!! GameMaster primario caduto. Mi promuovo a Master !!!");

        // Cambia registrazione nel DF da backup-master a game-master
        updateDFtoPrimary();

        // Notifica i Player
        broadcastNewMaster();


        addBehaviour(new ManageTurnBehaviour());

    }

    /**
     * Metodo che utilizziamo per notificare tutti i player che il gameMaster è cambiato
     */
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
            System.out.println("Notifica NEW_MASTER inviata a " + results.length + " giocatori.");
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Cancelliamo la vecchia registrazione del backup al DF e ci registriamo con il tipo game-master
     */
    private void updateDFtoPrimary() {
        try {
            DFService.deregister(this);

            // Poi ci registriamo come game-master
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("game-master");
            sd.setName("exploding-kittens-promoted");
            dfd.addServices(sd);
            DFService.register(this, dfd);
            System.out.println("Backup registrato come nuovo GameMaster nel DF.");
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Replicazione logica del GameMasterAgent primario.
     */
    private class ManageTurnBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            if (gameState.isGameOver()) {
                announceWinner();
                return;
            }

            Player current = gameState.getCurrentPlayer();

            for (Player p : gameState.getActivePlayers()) {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(new AID(p.getAgentName(), true));
                msg.setContent(p.getAgentName().equals(current.getAgentName())
                        ? Messages.YOUR_TURN
                        : Messages.TURN_OF + current.getNickname());
                send(msg);
            }

            addBehaviour(new HandleActionBehaviour());
        }
    }

    private class HandleActionBehaviour extends CyclicBehaviour {
        private ACLMessage pendingAction   = null; // azione in attesa della mano
        private String     pendingCatTarget = null; // target Cat Card in attesa della mano del target


        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();

            if (msg != null) {
                String content = msg.getContent();

                if (content.startsWith(Messages.HAND_RESPONSE)) {
                    String serializedHand = content.substring(Messages.HAND_RESPONSE.length());

                    if (pendingCatTarget != null) {
                        if (pendingAction != null) {
                            processCatCardWithTargetHand(pendingAction, serializedHand);
                        }
                        pendingAction = null;
                        pendingCatTarget = null;
                    }
                    else if (pendingAction != null) {
                        processActionWithHand(pendingAction, serializedHand);

                        if (pendingCatTarget == null) {
                            pendingAction = null;
                        }
                    }
                    return;
                }

                if (msg.getPerformative() != ACLMessage.REQUEST) {
                    block();
                    return;
                }

                Player current = gameState.getCurrentPlayer();
                if (!msg.getSender().getName().equals(current.getAgentName())) {
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.DISCONFIRM);
                    reply.setContent(Messages.NOT_YOUR_TURN);
                    send(reply);
                    return;
                }

                if (content.equals(Messages.DRAW)) {
                    handleDraw(msg);
                } else if (content.startsWith(Messages.PLAY) || content.startsWith(Messages.CAT_CARD_PLAY)) {
                    pendingAction = msg; // Salva l'azione
                    ACLMessage query = new ACLMessage(ACLMessage.REQUEST);
                    query.addReceiver(msg.getSender());
                    query.setContent(Messages.REQUEST_HAND);
                    send(query);
                } else if (content.startsWith(Messages.DEFUSE_PLAY)) {
                    handleDefuse(msg);
                } else if (content.equals(Messages.PLAYER_ELIMINATED)) {
                    handleElimination(msg);
                }

            } else {
                block();
            }
        }
        private void processActionWithHand(ACLMessage originalMsg, String serializedHand) {
            String content = originalMsg.getContent();

            List<CardType> hand = Arrays.stream(serializedHand.split(","))
                    .filter(s -> !s.isEmpty())
                    .map(CardType::valueOf)
                    .collect(Collectors.toList());


            if (content.startsWith(Messages.PLAY)) {
                String cardTypeName = content.substring(Messages.PLAY.length());
                CardType type = CardType.valueOf(cardTypeName);


                if (!hand.contains(type)) {
                    ACLMessage reply = originalMsg.createReply();
                    reply.setPerformative(ACLMessage.DISCONFIRM);
                    reply.setContent(Messages.CARD_NOT_IN_HAND);
                    send(reply);
                    return;
                }

                notifyRemoveCard(originalMsg.getSender(), type.name());

                switch (type) {
                    case SKIP           -> handleSkip(originalMsg);
                    case ATTACK         -> handleAttack(originalMsg);
                    case SHUFFLE        -> handleShuffle(originalMsg);
                    case SEE_THE_FUTURE -> handleSeeTheFuture(originalMsg);
                    case CAT_CARD       -> {
                        prepareCatCard(originalMsg);
                    }
                    default -> {
                        ACLMessage reply = originalMsg.createReply();
                        reply.setPerformative(ACLMessage.DISCONFIRM);
                        reply.setContent(Messages.CARD_NOT_IN_HAND);
                        send(reply);
                    }
                }

            } else if (content.startsWith(Messages.CAT_CARD_PLAY)) {
                long catCount = hand.stream().filter(t -> t == CardType.CAT_CARD).count();
                if (catCount < 2) {
                    System.out.println(CAT_LOG + "Meno di 2 CAT_CARD -> DISCONFIRM");
                    ACLMessage reply = originalMsg.createReply();
                    reply.setPerformative(ACLMessage.DISCONFIRM);
                    reply.setContent(Messages.CARD_NOT_IN_HAND);
                    send(reply);
                    return;
                }
                System.out.println(CAT_LOG + "CAT_CARD_PLAY valido -> prepareCatCard");
                prepareCatCard(originalMsg);
            }
        }

        // Cat Card: chiede la mano del target per rubare
        private void prepareCatCard(ACLMessage originalMsg) {
            String[] parts = originalMsg.getContent().split(":");
            if (parts.length < 2) {
                ACLMessage reply = originalMsg.createReply();
                reply.setPerformative(ACLMessage.DISCONFIRM);
                reply.setContent(Messages.MISSING_TARGET);
                send(reply);
                return;
            }

            String targetLocalName = parts[1];
            System.out.println(CAT_LOG + "target richiesto=" + targetLocalName);

            Player target = gameState.getActivePlayers().stream()
                    .filter(p -> p.getNickname().contains(targetLocalName)
                            || p.getAgentName().contains(targetLocalName))
                    .findFirst()
                    .orElse(null);

            if (target == null) {
                System.out.println(CAT_LOG + "Target non valido/non trovato");
                ACLMessage reply = originalMsg.createReply();
                reply.setPerformative(ACLMessage.DISCONFIRM);
                reply.setContent(Messages.INVALID_TARGET);
                send(reply);
                return;
            }

            pendingAction    = originalMsg;
            pendingCatTarget = target.getAgentName();

            System.out.println(CAT_LOG + "Target trovato=" + pendingCatTarget + " -> REQUEST_HAND al target");

            ACLMessage query = new ACLMessage(ACLMessage.REQUEST);
            query.addReceiver(new AID(target.getAgentName(), true));
            query.setContent(Messages.REQUEST_HAND);
            send(query);

            System.out.println(CAT_LOG + "REQUEST_HAND inviato al target " + target.getAgentName());
        }

        private void processCatCardWithTargetHand(ACLMessage originalMsg, String serializedTargetHand) {
            System.out.println(CAT_LOG + "processCatCardWithTargetHand handRaw=" + serializedTargetHand + " target=" + pendingCatTarget);

            List<String> targetCards = Arrays.stream(serializedTargetHand.split(","))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            System.out.println(CAT_LOG + "targetCards=" + targetCards);

            if (targetCards.isEmpty()) {
                System.out.println(CAT_LOG + "Target senza carte -> DISCONFIRM");
                ACLMessage reply = originalMsg.createReply();
                reply.setPerformative(ACLMessage.DISCONFIRM);
                reply.setContent(Messages.INVALID_TARGET);
                send(reply);
                return;
            }

            String stolenType = targetCards.get(new Random().nextInt(targetCards.size()));
            System.out.println(CAT_LOG + "Carta rubata=" + stolenType);

            notifyRemoveCard(originalMsg.getSender(), "CAT_CARD");
            notifyRemoveCard(originalMsg.getSender(), "CAT_CARD");

            ACLMessage removeFromTarget = new ACLMessage(ACLMessage.INFORM);
            removeFromTarget.addReceiver(new AID(pendingCatTarget, true));
            removeFromTarget.setContent(Messages.REMOVE_CARD + stolenType);
            send(removeFromTarget);
            System.out.println(CAT_LOG + "REMOVE_CARD inviato al target");

            ACLMessage addToThief = new ACLMessage(ACLMessage.INFORM);
            addToThief.addReceiver(originalMsg.getSender());
            addToThief.setContent(Messages.ADD_CARD + stolenType);
            send(addToThief);
            System.out.println(CAT_LOG + "ADD_CARD inviato al ladro");

            ACLMessage reply = originalMsg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent(Messages.YOU_STOLE + stolenType);
            send(reply);
            System.out.println(CAT_LOG + "CONFIRM inviato al ladro");

            ACLMessage notifyTarget = new ACLMessage(ACLMessage.INFORM);
            notifyTarget.addReceiver(new AID(pendingCatTarget, true));
            notifyTarget.setContent(Messages.STOLEN_FROM_YOU + stolenType);
            send(notifyTarget);
            System.out.println(CAT_LOG + "Notifica furto inviata al target");

            notifyRefresh(originalMsg.getSender());
            notifyRefresh(new AID(pendingCatTarget, true));
            System.out.println(CAT_LOG + "REFRESH inviato a ladro e target");
        }

        private void handleDraw(ACLMessage msg) {
            Card drawn = deck.removeTopCard();

            if (drawn.getType() == CardType.EXPLODING_KITTEN) {
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setContent(Messages.DREW_KITTEN);
                send(reply);

            } else {
                ACLMessage addCard = new ACLMessage(ACLMessage.INFORM);
                addCard.addReceiver(msg.getSender());
                addCard.setContent(Messages.ADD_CARD + drawn.getType().name());
                send(addCard);

                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setContent(Messages.DREW + drawn.getType().name());
                send(reply);

                notifyRefresh(msg.getSender());
                if (drawn.getType() != CardType.EXPLODING_KITTEN) {
                    gameState.nextTurn();
                    myAgent.removeBehaviour(this);
                    addBehaviour(new ManageTurnBehaviour());
                }
            }
        }

        private void handleSkip(ACLMessage msg) {
            notifyRefresh(msg.getSender());

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent(Messages.SKIP_OK);
            send(reply);

            gameState.nextTurn();
            myAgent.removeBehaviour(this);
            addBehaviour(new ManageTurnBehaviour());
        }

        private void handleAttack(ACLMessage msg) {
            notifyRefresh(msg.getSender());

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent(Messages.ATTACK_OK);
            send(reply);

            gameState.nextTurn();
            gameState.setTurnsToPlay(2);
            myAgent.removeBehaviour(this);
            addBehaviour(new ManageTurnBehaviour());
        }

        private void handleShuffle(ACLMessage msg) {
            List<Card> cards = deck.getCards();
            Collections.shuffle(cards);
            deck.setCards(cards);

            notifyRefresh(msg.getSender());

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent(Messages.SHUFFLE_OK);
            send(reply);
        }

        private void handleSeeTheFuture(ACLMessage msg) {
            int count = Math.min(3, deck.size()); //Gestione caso in cui nel mazzo siano rimaste meno di 3 carte
            List<Card> cardsToShow = deck.peekTop(count);

            StringBuilder sb = new StringBuilder(Messages.SEE_THE_FUTURE);
            for (Card c : cardsToShow) sb.append(c.getType().name()).append(",");

            notifyRefresh(msg.getSender());

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            reply.setContent(sb.toString());
            send(reply);
        }

        private void handleDefuse(ACLMessage msg) {
            String[] parts = msg.getContent().split(":");
            System.out.println("Prime carte del mazzo dopo Defuse: ");
            deck.getCards().stream().limit(3).forEach(c -> System.out.println("- " + c.getType()));
            notifyRefresh(msg.getSender());

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent(Messages.DEFUSED);
            send(reply);

            broadcastToAll(msg.getSender().getLocalName() + " ha neutralizzato l'Exploding Kitten!");

            gameState.nextTurn();
            myAgent.removeBehaviour(this);
            addBehaviour(new ManageTurnBehaviour());
        }

        private void handleElimination(ACLMessage msg) {
            Player eliminated = findPlayerByAgentName(msg.getSender().getName());
            if (eliminated != null) {
                gameState.removePlayer(eliminated);
                broadcastToAll(eliminated.getNickname() + " è stato eliminato!");
                System.out.println(eliminated.getNickname() + " eliminato.");
            }

            if (gameState.isGameOver()) {
                announceWinner();
            } else {
                myAgent.removeBehaviour(this);
                addBehaviour(new ManageTurnBehaviour());
            }
        }

        private void notifyRemoveCard(AID target, String cardType) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(target);
            msg.setContent(Messages.REMOVE_CARD + cardType);
            send(msg);
        }

        private void notifyRefresh(AID target) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(target);
            msg.setContent(Messages.REFRESH_HAND);
            send(msg);
        }
    }

    private void announceWinner() {
        Player winner = gameState.getWinner();
        if (winner != null) {
            broadcastToAll(Messages.WINNER + winner.getNickname());
            System.out.println("Vincitore: " + winner.getNickname());
        }
    }

    private void broadcastToAll(String content) {
        for (Player p : gameState.getActivePlayers()) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(new AID(p.getAgentName(), true));
            msg.setContent(content);
            send(msg);
        }
    }

    private Player findPlayerByAgentName(String agentName) {
        return gameState.getActivePlayers().stream()
                .filter(p -> p.getAgentName().equals(agentName))
                .findFirst()
                .orElse(null);
    }
}
