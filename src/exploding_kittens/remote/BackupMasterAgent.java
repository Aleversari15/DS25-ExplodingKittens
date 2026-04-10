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

import java.util.*;
import java.util.stream.Collectors;

/**
 * BackupMasterAgent — rimane in ascolto degli heartbeat dal GameMaster primario.
 * Se il primario non risponde entro TIMEOUT ms, si promuove a nuovo GameMaster
 * e continua la partita dal punto in cui era rimasta.
 *
 * Un unico CyclicBehaviour gestisce sia la fase di backup (heartbeat)
 * che la fase di master (azioni di gioco), evitando che due behaviour
 * si rubino i messaggi dalla stessa coda.
 */
public class BackupMasterAgent extends Agent {
    private GameState gameState;
    private Deck      deck;
    private long lastHeartbeatTime = 0;
    private boolean promoted       = false; // true dopo la promozione a master
    private static final long   TIMEOUT      = 10_000; // ms senza heartbeat prima di promuoversi
    private static final long   TICKER_PERIOD = 2_000; // ms tra i controlli del timeout
    private static final String CAT_LOG      = "[BackupMaster - CAT_CARD] ";
    private ACLMessage pendingAction    = null;
    private String     pendingCatTarget = null;


    @Override
    protected void setup() {
        registerInDF();

        addBehaviour(new MainBehaviour());

        // Ticker separato solo per il controllo del timeout
        addBehaviour(new TickerBehaviour(this, TICKER_PERIOD) {
            @Override
            protected void onTick() {
                if (!promoted
                        && lastHeartbeatTime > 0
                        && System.currentTimeMillis() - lastHeartbeatTime > TIMEOUT) {
                    promoteToMaster();
                    stop();
                }
            }
        });
    }


    private class MainBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();

            if (msg == null) {
                block();
                return;
            }

            String content = msg.getContent();
            if (content == null) return;

            if (!promoted) {
                // ---- Fase BACKUP: aspetta solo heartbeat ----
                handleAsBackup(msg, content);
            } else {
                // ---- Fase MASTER: gestisce le azioni di gioco ----
                handleAsMaster(msg, content);
            }
        }

        /**
         * FASE BACKUP: qui consideraimo solo i messaggi HEARTBEATS, gli altri non devono essere gestiti.
         * @param msg
         * @param content
         */
        private void handleAsBackup(ACLMessage msg, String content) {
            if (content.startsWith(Messages.HEARTBEAT)) {
                lastHeartbeatTime = System.currentTimeMillis();
                String stateData = content.substring(Messages.HEARTBEAT.length() + 1);
                reconstructState(stateData);
            }
        }

        /**
         * FASE MASTER: dopo essere stat promosso a master, dobbiamo gestire tutti i messaggi riguardanti il gioco.
         * @param msg
         * @param content
         */
        private void handleAsMaster(ACLMessage msg, String content) {

            if (content.startsWith(Messages.JOIN)) {
                handleJoinRequest(msg, content);
                return;
            }

            if (content.startsWith(Messages.HAND_RESPONSE)) {
                String serializedHand = content.substring(Messages.HAND_RESPONSE.length());
                if (pendingCatTarget != null && pendingAction != null) {
                    processCatCardWithTargetHand(pendingAction, serializedHand);
                    pendingAction    = null;
                    pendingCatTarget = null;
                } else if (pendingAction != null) {
                    processActionWithHand(pendingAction, serializedHand);
                    if (pendingCatTarget == null) pendingAction = null;
                }
                return;
            }

            // Solo REQUEST arrivano dai PlayerAgent per le azioni
            if (msg.getPerformative() != ACLMessage.REQUEST) return;

            Player current = gameState.getCurrentPlayer();
            if (current == null) return;

            if (!msg.getSender().getName().equals(current.getAgentName())) {
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.DISCONFIRM);
                reply.setContent(Messages.NOT_YOUR_TURN);
                send(reply);
                return;
            }

            if (content.equals(Messages.DRAW)) {
                handleDraw(msg);
            } else if (content.startsWith(Messages.PLAY)
                    || content.startsWith(Messages.CAT_CARD_PLAY)) {
                pendingAction = msg;
                ACLMessage query = new ACLMessage(ACLMessage.REQUEST);
                query.addReceiver(msg.getSender());
                query.setContent(Messages.REQUEST_HAND);
                send(query);
            } else if (content.startsWith(Messages.DEFUSE_PLAY)) {
                handleDefuse(msg);
            } else if (content.equals(Messages.PLAYER_ELIMINATED)) {
                handleElimination(msg);
            }
        }


        private void processActionWithHand(ACLMessage originalMsg, String serializedHand) {
            String content = originalMsg.getContent();

            List<CardType> hand = Arrays.stream(serializedHand.split(","))
                    .filter(s -> !s.isEmpty())
                    .map(CardType::valueOf)
                    .collect(Collectors.toList());

            if (content.startsWith(Messages.PLAY)) {
                CardType type = CardType.valueOf(content.substring(Messages.PLAY.length()));

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
                    case CAT_CARD       -> prepareCatCard(originalMsg);
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
                    ACLMessage reply = originalMsg.createReply();
                    reply.setPerformative(ACLMessage.DISCONFIRM);
                    reply.setContent(Messages.CARD_NOT_IN_HAND);
                    send(reply);
                    return;
                }
                prepareCatCard(originalMsg);
            }
        }


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
            Player target = gameState.getActivePlayers().stream()
                    .filter(p -> p.getNickname().contains(targetLocalName)
                            || p.getAgentName().contains(targetLocalName))
                    .findFirst().orElse(null);

            if (target == null) {
                ACLMessage reply = originalMsg.createReply();
                reply.setPerformative(ACLMessage.DISCONFIRM);
                reply.setContent(Messages.INVALID_TARGET);
                send(reply);
                return;
            }

            pendingAction    = originalMsg;
            pendingCatTarget = target.getAgentName();

            ACLMessage query = new ACLMessage(ACLMessage.REQUEST);
            query.addReceiver(new AID(target.getAgentName(), true));
            query.setContent(Messages.REQUEST_HAND);
            send(query);
        }

        private void processCatCardWithTargetHand(ACLMessage originalMsg, String serializedTargetHand) {
            List<String> targetCards = Arrays.stream(serializedTargetHand.split(","))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            if (targetCards.isEmpty()) {
                ACLMessage reply = originalMsg.createReply();
                reply.setPerformative(ACLMessage.DISCONFIRM);
                reply.setContent(Messages.INVALID_TARGET);
                send(reply);
                return;
            }

            String stolenType = targetCards.get(new Random().nextInt(targetCards.size()));

            notifyRemoveCard(originalMsg.getSender(), "CAT_CARD");
            notifyRemoveCard(originalMsg.getSender(), "CAT_CARD");

            ACLMessage removeFromTarget = new ACLMessage(ACLMessage.INFORM);
            removeFromTarget.addReceiver(new AID(pendingCatTarget, true));
            removeFromTarget.setContent(Messages.REMOVE_CARD + stolenType);
            send(removeFromTarget);

            ACLMessage addToThief = new ACLMessage(ACLMessage.INFORM);
            addToThief.addReceiver(originalMsg.getSender());
            addToThief.setContent(Messages.ADD_CARD + stolenType);
            send(addToThief);

            ACLMessage reply = originalMsg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent(Messages.YOU_STOLE + stolenType);
            send(reply);

            ACLMessage notifyTarget = new ACLMessage(ACLMessage.INFORM);
            notifyTarget.addReceiver(new AID(pendingCatTarget, true));
            notifyTarget.setContent(Messages.STOLEN_FROM_YOU + stolenType);
            send(notifyTarget);

            notifyRefresh(originalMsg.getSender());
            notifyRefresh(new AID(pendingCatTarget, true));
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
                gameState.nextTurn();
                nextTurn();
            }
        }

        private void handleSkip(ACLMessage msg) {
            notifyRefresh(msg.getSender());
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent(Messages.SKIP_OK);
            send(reply);
            gameState.nextTurn();
            nextTurn();
        }

        private void handleJoinRequest(ACLMessage msg, String content) {

            String playerName = msg.getSender().getName();
            //controlla che non ci siano già giocatori con lo stesso nome
            Player existing = findPlayerByAgentName(playerName);

            if (existing == null) {
                Player newPlayer = new Player(playerName, msg.getSender().getLocalName());
                gameState.addPlayer(newPlayer);
                System.out.println("Giocatore ri-annunciato aggiunto: " + playerName);
            }

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent(Messages.JOINED);
            send(reply);

            int limit = 2; // TODO: Valore di default o recuperato
            if (gameState.getActivePlayers().size() == limit) {
                System.out.println("Lobby piena dopo il failover! Avvio partita...");
                deck = DeckBuilder.prepareBaseDeck(limit);
                Map<String, String> hands = DeckBuilder.buildPlayerHands(deck, gameState.getActivePlayers());
                DeckBuilder.insertExplodingKittens(deck, limit);

                for (Player p : gameState.getActivePlayers()) {
                    ACLMessage handMsg = new ACLMessage(ACLMessage.INFORM);
                    handMsg.addReceiver(new AID(p.getAgentName(), true));
                    handMsg.setContent(Messages.HAND_INIT + hands.get(p.getAgentName()));
                    send(handMsg);
                }

                gameState.setCurrentPlayerIndex(0);
                nextTurn();
            }

            System.out.println("[DEBUG-BACKUP] Ricevuta richiesta JOIN da: " + msg.getSender().getLocalName());
            System.out.println("[DEBUG-BACKUP] Totale giocatori ora: " + gameState.getActivePlayers().size() + "/" + limit);
        }

        private void handleAttack(ACLMessage msg) {
            notifyRefresh(msg.getSender());
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent(Messages.ATTACK_OK);
            send(reply);
            gameState.nextTurn();
            gameState.setTurnsToPlay(2);
            nextTurn();
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
            int count = Math.min(3, deck.size());
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
            int position = Integer.parseInt(parts[1]);
            position = Math.min(position, deck.size());
            deck.insertCard(
                    new Card(CardType.EXPLODING_KITTEN, "Exploding Kitten", "Sei esploso!"),
                    position
            );
            notifyRefresh(msg.getSender());
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent(Messages.DEFUSED);
            send(reply);
            broadcastToAll(msg.getSender().getLocalName() + " ha neutralizzato l'Exploding Kitten!");
            gameState.nextTurn();
            nextTurn();
        }

        private void handleElimination(ACLMessage msg) {
            Player eliminated = findPlayerByAgentName(msg.getSender().getName());
            if (eliminated != null) {
                gameState.removePlayer(eliminated);
                broadcastToAll(eliminated.getNickname() + " e' stato eliminato!");
            }
            if (gameState.isGameOver()) {
                announceWinner();
            } else {
                nextTurn();
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

        private void nextTurn() {
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
        }
    }


    private void promoteToMaster() {
        System.out.println("[DEBUG-BACKUP] !!! PROMOZIONE !!! Sono il nuovo Master. Giocatori conosciuti: " +
                gameState.getActivePlayers().stream().map(Player::getNickname).collect(Collectors.joining(",")));
        promoted = true;

        updateDFtoPrimary();
        broadcastNewMaster();

        if (gameState != null && !gameState.isGameOver() && !gameState.getActivePlayers().isEmpty()) {
            Player current = gameState.getCurrentPlayer();
            for (Player p : gameState.getActivePlayers()) {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(new AID(p.getAgentName(), true));
                msg.setContent(p.getAgentName().equals(current.getAgentName())
                        ? Messages.YOUR_TURN
                        : Messages.TURN_OF + current.getNickname());
                send(msg);
            }
        }

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
            System.out.println("Backup registrato come nuovo GameMaster nel DF.");
        } catch (Exception e) { e.printStackTrace(); }
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

    private void reconstructState(String data) {
        try {
            String[] parts = data.split(":", 4);
            if (parts.length < 4) return;

            if (gameState == null) gameState = new GameState();
            if (deck == null)      deck      = new Deck();

            // 1. Indice Giocatore Corrente
            try {
                gameState.setCurrentPlayerIndex(Integer.parseInt(parts[0]));
            } catch (NumberFormatException e) {
                gameState.setCurrentPlayerIndex(0);
            }

            // 2. Turni Rimanenti
            try {
                gameState.setTurnsToPlay(Integer.parseInt(parts[1]));
            } catch (NumberFormatException e) {
                gameState.setTurnsToPlay(1);
            }

            // 3. Ricostruzione Mazzo
            List<Card> restoredCards = new ArrayList<>();
            String deckData = parts[2];
            if (!deckData.isEmpty() && !deckData.equals("WAITING_LOBBY") && !deckData.contains("WAITING_FOR_PLAYERS")) {
                for (String cardName : deckData.split(",")) {
                    String name = cardName.trim();
                    if (!name.isEmpty()) {
                        try {
                            restoredCards.add(new Card(CardType.valueOf(name)));
                        } catch (Exception e) { /* Ignora tipi carta errati */ }
                    }
                }
            }
            deck.setCards(restoredCards);

            // 4. Ricostruzione Giocatori
            String playersPart = parts[3];
            if (!playersPart.isEmpty() && !playersPart.equals("null")) {
                List<Player> restoredPlayers = new ArrayList<>();
                for (String pData : playersPart.split("\\|")) {
                    String[] pParts = pData.split(",");
                    if (pParts.length >= 2) {
                        // Pulizia nickname (Player_Giocatore1 -> Giocatore1)
                        String rawNick = pParts[1];
                        String cleanNick = rawNick.replace("Player_", "");
                        restoredPlayers.add(new Player(pParts[0], cleanNick));
                    }
                }
                // Aggiorna la lista solo se abbiamo trovato dei giocatori
                if (!restoredPlayers.isEmpty()) {
                    gameState.setActivePlayers(restoredPlayers);
                }
            }

        } catch (Exception e) {
            System.err.println("Errore ricostruzione stato: " + e.getMessage());
            e.printStackTrace(); // Utile in debug per vedere l'intera traccia dell'errore
        }


        if (gameState.getActivePlayers() != null) {
            System.out.println("[DEBUG-BACKUP] Stato sincronizzato. Giocatori attivi: " +
                    gameState.getActivePlayers().size() + " | Turno di: " + gameState.getCurrentPlayerIndex());
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
                .findFirst().orElse(null);
    }
}