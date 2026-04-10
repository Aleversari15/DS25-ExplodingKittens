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

public class GameMasterAgent extends Agent {

    private GameState gameState;
    private Deck deck;
    private int expectedPlayers = -1; //significa che non è ancora stato impostato
    private AID backupMasterAID;
    private static final String CAT_LOG = "[GameMaster - CAT_CARD] ";

    @Override
    protected void setup() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());

            ServiceDescription sd = new ServiceDescription();
            sd.setType("game-master");
            sd.setName("exploding-kittens");

            dfd.addServices(sd);
            DFService.register(this, dfd);

            System.out.println("GameMaster registrato nel DF");
        } catch (Exception e) {
            e.printStackTrace();
        }

        Object[] args = getArguments();
        if(args !=null && args.length>0) {
            expectedPlayers =  Integer.parseInt(args[0].toString());
        }
        gameState = new GameState();
        deck      = new Deck();

        startHeartbeat();

        System.out.println("GameMaster avviato, aspetto " + expectedPlayers + " giocatori...");
        addBehaviour(new WaitForPlayersBehaviour());
    }


    private class WaitForPlayersBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String content = msg.getContent();

                // Controlliamo se il messaggio inizia con la costante JOIN
                if (content != null && content.startsWith(Messages.JOIN)) {

                    // Se expectedPlayers è -1, questo è il PRIMO giocatore!
                    if (expectedPlayers == -1) {
                        try {
                            // Estrapoliamo il numero di giocatori (es. da "JOIN:4")
                            String[] parts = content.split(":");
                            if (parts.length > 1) {
                                expectedPlayers = Integer.parseInt(parts[1]);
                            } else {
                                expectedPlayers = 2; // Valore di fallback se il parsing fallisce
                            }
                            System.out.println("Lobby creata! Il primo giocatore ha impostato il limite a: " + expectedPlayers);
                        } catch (NumberFormatException e) {
                            expectedPlayers = 2;
                        }
                    }



                    // Controlliamo se c'è ancora posto nella lobby
                    if (gameState.getActivePlayers().size() < expectedPlayers) {
                        String playerName = msg.getSender().getName();

                        // Piccolo controllo di sicurezza per evitare doppi ingressi
                        if (findPlayerByAgentName(playerName) == null) {
                            Player player = new Player(playerName, msg.getSender().getLocalName());
                            gameState.addPlayer(player);

                            sincronize();

                            System.out.println("Giocatore registrato: " + playerName + " (" + gameState.getActivePlayers().size() + "/" + expectedPlayers + ")");

                            ACLMessage reply = msg.createReply();
                            reply.setPerformative(ACLMessage.CONFIRM);
                            reply.setContent(Messages.JOINED);
                            send(reply);

                            // Se abbiamo raggiunto il numero richiesto dal primo giocatore, iniziamo
                            if (gameState.getActivePlayers().size() == expectedPlayers) {
                                System.out.println("Lobby piena! Avvio partita in corso...");
                                myAgent.removeBehaviour(this);
                                addBehaviour(new StartGameBehaviour());
                            }
                        }
                    } else {
                        // La lobby è piena, rifiutiamo i giocatori in eccesso
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.REFUSE);
                        reply.setContent("LOBBY_FULL"); // Potresti voler aggiungere questo al tuo file Messages
                        send(reply);
                    }
                }
            } else {
                block();
            }
        }
    }

    private void sincronize() {
        if(backupMasterAID == null) backupMasterAID = findBackup();
        if(backupMasterAID != null) {
            ACLMessage hb = new ACLMessage(ACLMessage.INFORM);
            hb.addReceiver(backupMasterAID);
            hb.setContent(Messages.HEARTBEAT + ":" + serializeState());
            send(hb);
        }
    }

    /**
     * Tramite il DeckBuilder viene costruito il mazzo e vengono distribuite le carte ai giocatori.
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
            addBehaviour(new ManageTurnBehaviour());
        }
    }


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

                //TODO: dopo verifica, modificare anche backup agent
                if (msg.getPerformative() != ACLMessage.REQUEST && msg.getPerformative() != ACLMessage.INFORM ) {
                    block();
                    return;
                }

                if (content.equals(Messages.PLAYER_ELIMINATED)) {
                    handleElimination(msg);
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
            int position   = Integer.parseInt(parts[1]);

            deck.insertCard(
                    new Card(CardType.EXPLODING_KITTEN, "Exploding Kitten", "Sei esploso!"),
                    position
            );

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


    private void broadcastToAll(String content) {
        for (Player p : gameState.getActivePlayers()) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(new AID(p.getAgentName(), true));
            msg.setContent(content);
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

    private Player findPlayerByAgentName(String agentName) {
        return gameState.getActivePlayers().stream()
                .filter(p -> p.getAgentName().equals(agentName))
                .findFirst()
                .orElse(null);
    }

    private void broadcastPlayersList() {
        StringBuilder sb = new StringBuilder("PLAYERS_LIST:");
        for (int i = 0; i < gameState.getActivePlayers().size(); i++) {
            sb.append(gameState.getActivePlayers().get(i).getNickname());
            if (i < gameState.getActivePlayers().size() - 1) sb.append(",");
        }
        broadcastToAll(sb.toString());
    }

    private void startHeartbeat() {

        addBehaviour(new TickerBehaviour(this, 3000) {
            @Override
            protected void onTick() {
                System.out.println("[DEBUG-GM] Invio Heartbeat. Stato: " + serializeState());
                if(backupMasterAID == null){
                    backupMasterAID = findBackup();
                }
                else{
                    ACLMessage hb = new ACLMessage(ACLMessage.INFORM);
                    hb.addReceiver(backupMasterAID);
                    hb.setContent(Messages.HEARTBEAT + ":" + serializeState());
                    myAgent.send(hb);
                }
            }
        });
    }

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

    /**
     * Metodo che ricostruisce una stringa rappresentante il GameState corrente
     * @return una stringa che rappresenta lo stato del gioco
     */
    private String serializeState() {
        StringBuilder sb = new StringBuilder();

        // 1. INDICE GIOCATORE CORRENTE (parts[0])
        sb.append(gameState.getCurrentPlayerIndex()).append(":");

        // 2. TURNI RIMANENTI (parts[1])
        sb.append(gameState.getTurnsToPlay()).append(":");

        // 3. IL MAZZO (parts[2])
        if (deck != null && deck.getCards() != null && !deck.getCards().isEmpty()) {
            String deckState = deck.getCards().stream()
                    .map(card -> card.getType().name())
                    .collect(Collectors.joining(","));
            sb.append(deckState);
        } else {
            sb.append("WAITING_FOR_PLAYERS");
        }
        sb.append(":");

        // 4. GIOCATORI ATTIVI (parts[3])
        // Usiamo il pipe | per separare i giocatori e la virgola , per i dettagli
        String playersState = gameState.getActivePlayers().stream()
                .map(p -> p.getAgentName() + "," + p.getNickname())
                .collect(Collectors.joining("|"));
        sb.append(playersState);

        return sb.toString();
    }
}

