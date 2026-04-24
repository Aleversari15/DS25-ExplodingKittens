package explodingkittens.remote;

import explodingkittens.game.model.*;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Classe base che contiene tutta la logica di gioco condivisa
 * tra GameMasterAgent e BackupMasterAgent.
 * Gestisce:
 * - Lobby (JOIN, NICKNAME_AND_LOBBY_CHECK)
 * - Avvio partita
 * - Azioni di gioco (carte, turni, eliminazioni)
 * - Serializzazione/deserializzazione stato
 * - Heartbeat client e disconnessioni
 * - Behaviour di ascolto messaggi (LobbyBehaviour, HandleActionBehaviour)
 * Le sottoclassi implementano solo setup(), registrazione DF,
 * e la logica specifica del loro ruolo (heartbeat verso backup, promozione).
 */
public abstract class AbstractMasterAgent extends Agent {

    protected GameState gameState;
    protected Deck deck;
    protected ACLMessage pendingAction = null;
    protected String pendingCatTarget = null;
    protected Map<String, Long> clientsAliveRegister = new HashMap<>();
    protected static final long PLAYER_TIMEOUT = 10000;
    protected boolean gameStarted = false;
    protected int expectedPlayers = -1;
    protected LobbyBehaviour lobbyBehaviour;

    /**
     * Behaviour condiviso per la gestione della lobby.
     * Attende JOIN e NICKNAME_AND_LOBBY_CHECK dai player.
     * Quando la lobby è piena, avvia StartGameBehaviour.
     */
    protected class LobbyBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.receive(mt);

            if (msg == null) { block(); return; }

            String content = msg.getContent();
            if (content == null) return;

            if (content.startsWith(Messages.NICKNAME_AND_LOBBY_CHECK)) {
                handleNicknameCheck(msg, content);
                return;
            }

            if (content.startsWith(Messages.JOIN)) {
                handleJoin(msg, content);
            }
        }
    }

    /**
     * Behaviour condiviso per la gestione della logica di gioco.
     * Attivo dopo l'avvio della partita.
     */
    protected class HandleActionBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg == null) { block(); return; }

            // Ignora messaggi di sistema JADE
            String senderLocal = msg.getSender().getLocalName();
            if (senderLocal.equals("ams") || senderLocal.equals("df")) return;

            String content = msg.getContent();
            if (content == null) return;

            if (content.startsWith(Messages.JOIN) ||
                    content.startsWith(Messages.NICKNAME_AND_LOBBY_CHECK)) {
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.REFUSE);
                reply.setContent(Messages.LOBBY_FULL);
                send(reply);
                return;
            }

            processGameMessage(msg);
        }
    }

    // =========================================================
    // GESTIONE LOBBY
    // =========================================================

    /**
     * Gestisce la verifica del nickname e disponibilità della lobby.
     * Logica comune a GameMaster e BackupMaster.
     */
    protected void handleNicknameCheck(ACLMessage msg, String content) {
        String requestedNick = content.substring(Messages.NICKNAME_AND_LOBBY_CHECK.length()).trim();

        boolean alreadyUsed = gameState.getActivePlayers().stream()
                .anyMatch(p -> p.getNickname().equalsIgnoreCase(requestedNick));

        ACLMessage reply = msg.createReply();
        reply.setPerformative(ACLMessage.INFORM);

        if (alreadyUsed) {
            reply.setContent(Messages.INVALID_NICKNAME);
        } else if (expectedPlayers != -1 &&
                gameState.getActivePlayers().size() >= expectedPlayers) {
            reply.setContent(Messages.LOBBY_FULL);
        } else {
            reply.setContent(expectedPlayers <= 0 ? Messages.VALID_HOST : Messages.VALID_GUEST);
        }
        send(reply);
    }

    /**
     * Gestisce la richiesta di JOIN di un player.
     * Logica comune a GameMaster e BackupMaster.
     */
    protected void handleJoin(ACLMessage msg, String content) {
        System.out.println("expectedPlayers: " + expectedPlayers);
        if (expectedPlayers == -1) {
            try {
                String[] parts = content.split(":");
                expectedPlayers = parts.length > 1 ? Integer.parseInt(parts[1]) : 2;
                System.out.println("[" + getLocalName() + "] Lobby creata con limite: " + expectedPlayers);
            } catch (NumberFormatException e) {
                expectedPlayers = 2;
            }
        }

        if (gameState.getActivePlayers().size() < expectedPlayers) {
            String playerName = msg.getSender().getName();
            if (findPlayerByAgentName(playerName) == null) {
                Player player = new Player(playerName, msg.getSender().getLocalName());
                gameState.addPlayer(player);
                onPlayerJoined(); // hook per sottoclassi (es. sincronizza con backup)

                System.out.println("[" + getLocalName() + "] Registrato: " + playerName
                        + " (" + gameState.getActivePlayers().size() + "/" + expectedPlayers + ")");

                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.CONFIRM);
                reply.setContent(Messages.JOINED);
                send(reply);

                if (gameState.getActivePlayers().size() == expectedPlayers) {
                    if (lobbyBehaviour != null) {
                        removeBehaviour(lobbyBehaviour);
                    }
                    setupAndStartGame();
                }
            }
        } else {
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.REFUSE);
            reply.setContent(Messages.LOBBY_FULL);
            send(reply);
        }
    }
    //------------------------------------------------
     //TODO: serve tenerlo qui?
    /**
     * chiamato ogni volta che un player si registra con successo.
     * Il GameMaster lo usa per sincronizzare il BackupMaster.
     * Il BackupMaster non fa nulla di extra.
     */
    protected void onPlayerJoined() {
        // default: nessuna azione — sovrascrivibile dalle sottoclassi
    }


    /**
     * Inizializza e avvia la partita:
     * - costruisce il mazzo
     * - distribuisce le carte iniziali
     * - inserisce gli Exploding Kittens
     * - notifica i giocatori
     * - avvia HandleActionBehaviour e il checker di disconnessioni
     */
    protected void setupAndStartGame() {
        if (gameStarted) return;
        gameStarted = true;

        deck = DeckBuilder.prepareBaseDeck(expectedPlayers);
        Map<String, String> hands = DeckBuilder.buildPlayerHands(deck, gameState.getActivePlayers());
        DeckBuilder.insertExplodingKittens(deck, expectedPlayers);

        for (Player player : gameState.getActivePlayers()) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(new AID(player.getAgentName(), true));
            msg.setContent(Messages.HAND_INIT + hands.get(player.getAgentName()));
            send(msg);
        }

        System.out.println("[" + getLocalName() + "] Partita avviata!");
        broadcastPlayersList();
        nextTurn();
        addBehaviour(new HandleActionBehaviour());
        startPlayerTimeoutChecker();
    }

    /**
     * Avvia il behaviour periodico per rilevare player disconnessi.
     */
    protected void startPlayerTimeoutChecker() {
        addBehaviour(new TickerBehaviour(this, 2000) {
            @Override
            protected void onTick() {
                checkDisconnectedPlayers();
            }
        });
    }

    /**
     * Processa i messaggi in arrivo smistandoli in messaggi che non dipendono dal turno
     * e azioni di gioco.
     */
    protected boolean processGameMessage(ACLMessage msg) {
        if (msg == null || msg.getContent() == null) return false;
        String content = msg.getContent();

        if (content.equals(Messages.HEARTBEAT_CLIENT)) {
            handlePlayerHeartbeat(msg);
            return true;
        }
        if (content.startsWith(Messages.HAND_RESPONSE)) {
            String serializedHand = content.substring(Messages.HAND_RESPONSE.length());
            if (pendingCatTarget != null && pendingAction != null) {
                processCatCardWithTargetHand(pendingAction, serializedHand);
                pendingAction = null;
                pendingCatTarget = null;
            } else if (pendingAction != null) {
                processActionWithHand(pendingAction, serializedHand);
                if (pendingCatTarget == null) pendingAction = null;
            }
            return true;
        }
        if (content.equals(Messages.PLAYER_ELIMINATED)) {
            handleElimination(msg);
            return true;
        }

        if (gameState == null || gameState.getActivePlayers().isEmpty()) {
            System.out.println("[WARN] processGameMessage: gameState non ancora pronto, messaggio ignorato: " + content);
            return false;
        }

        Player current = gameState.getCurrentPlayer();
        if (current == null) return false;

        if (!msg.getSender().getName().equals(current.getAgentName())) {
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.DISCONFIRM);
            reply.setContent(Messages.NOT_YOUR_TURN);
            send(reply);
            return true;
        }

        if (content.equals(Messages.DRAW)) {
            handleDraw(msg);
        } else if (content.startsWith(Messages.PLAY) ||
                content.startsWith(Messages.CAT_CARD_PLAY)) {
            pendingAction = msg;
            ACLMessage query = new ACLMessage(ACLMessage.REQUEST);
            query.addReceiver(msg.getSender());
            query.setContent(Messages.REQUEST_HAND);
            send(query);
        } else if (content.startsWith(Messages.DEFUSE_PLAY)) {
            handleDefuse(msg);
        } else {
            return false;
        }
        return true;
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
                sendDisconfirm(originalMsg, Messages.TWO_CAT_NOT_IN_HAND);
                return;
            }
            notifyRemoveCard(originalMsg.getSender(), type.name());

            switch (type) {
                case SKIP           -> handleSkip(originalMsg);
                case ATTACK         -> handleAttack(originalMsg);
                case SHUFFLE        -> handleShuffle(originalMsg);
                case SEE_THE_FUTURE -> handleSeeTheFuture(originalMsg);
                case CAT_CARD       -> prepareCatCard(originalMsg);
            }

        } else if (content.startsWith(Messages.CAT_CARD_PLAY)) {
            long catCount = hand.stream()
                    .filter(t -> t == CardType.CAT_CARD).count();
            if (catCount < 2) {
                sendDisconfirm(originalMsg, Messages.TWO_CAT_NOT_IN_HAND);
                return;
            }
            prepareCatCard(originalMsg);
        }
    }

    private void prepareCatCard(ACLMessage originalMsg) {
        String[] parts = originalMsg.getContent().split(":");
        if (parts.length < 2) {
            sendDisconfirm(originalMsg, Messages.MISSING_TARGET);
            return;
        }

        String targetLocalName = parts[1];
        Player target = gameState.getActivePlayers().stream()
                .filter(p -> p.getNickname().contains(targetLocalName)
                        || p.getAgentName().contains(targetLocalName))
                .findFirst().orElse(null);

        if (target == null) {
            sendDisconfirm(originalMsg, Messages.INVALID_TARGET);
            return;
        }

        pendingAction = originalMsg;
        pendingCatTarget = target.getAgentName();

        ACLMessage query = new ACLMessage(ACLMessage.REQUEST);
        query.addReceiver(new AID(target.getAgentName(), true));
        query.setContent(Messages.REQUEST_HAND);
        send(query);
    }

    private void processCatCardWithTargetHand(ACLMessage originalMsg,
                                              String serializedTargetHand) {
        List<String> targetCards = Arrays.stream(serializedTargetHand.split(","))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (targetCards.isEmpty()) {
            sendDisconfirm(originalMsg, Messages.INVALID_TARGET);
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

    protected void handleDraw(ACLMessage msg) {
        Card drawn = deck.removeTopCard();

        if (drawn.getType() == CardType.EXPLODING_KITTEN) {
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            reply.setContent(Messages.DREW_KITTEN + ":" + deck.size());
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

    protected void handleSkip(ACLMessage msg) {
        notifyRefresh(msg.getSender());
        ACLMessage reply = msg.createReply();
        reply.setPerformative(ACLMessage.CONFIRM);
        reply.setContent(Messages.SKIP_OK);
        send(reply);
        gameState.nextTurn();
        nextTurn();
    }

    protected void handleAttack(ACLMessage msg) {
        notifyRefresh(msg.getSender());
        ACLMessage reply = msg.createReply();
        reply.setPerformative(ACLMessage.CONFIRM);
        reply.setContent(Messages.ATTACK_OK);
        send(reply);

        Player current = gameState.getCurrentPlayer();
        do { gameState.nextTurn(); }
        while (current == gameState.getCurrentPlayer());
        gameState.setTurnsToPlay(2);
        nextTurn();
    }

    protected void handleShuffle(ACLMessage msg) {
        List<Card> cards = deck.getCards();
        Collections.shuffle(cards);
        deck.setCards(cards);
        notifyRefresh(msg.getSender());
        ACLMessage reply = msg.createReply();
        reply.setPerformative(ACLMessage.CONFIRM);
        reply.setContent(Messages.SHUFFLE_OK);
        send(reply);
    }

    protected void handleSeeTheFuture(ACLMessage msg) {
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

    protected void handleDefuse(ACLMessage msg) {
        String[] parts = msg.getContent().split(":");
        int position = Math.min(Integer.parseInt(parts[1]), deck.size());
        deck.insertCard(
                new Card(CardType.EXPLODING_KITTEN, "Exploding Kitten", "Sei esploso!"),
                position
        );
        notifyRefresh(msg.getSender());
        ACLMessage reply = msg.createReply();
        reply.setPerformative(ACLMessage.CONFIRM);
        reply.setContent(Messages.DEFUSED);
        send(reply);
        broadcastToAll(Messages.SHOW_DEFUSE_USED + msg.getSender().getLocalName());
        gameState.nextTurn();
        nextTurn();
    }

    protected void handleElimination(ACLMessage msg) {
        Player eliminated = findPlayerByAgentName(msg.getSender().getName());
        if (eliminated != null) {
            gameState.removePlayer(eliminated);
            broadcastToAll(eliminated.getNickname() + " e' stato eliminato!");
            broadcastPlayersList();
            System.out.println(eliminated.getNickname() + " eliminato.");
        }
        if (gameState.isGameOver()) announceWinner();
        else nextTurn();
    }

    protected void nextTurn() {
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

    /**
     * Serializza lo stato corrente del gioco in una stringa.
     * Usato dal GameMaster per inviare heartbeat al BackupMaster.
     */
    protected String serializeState() {
        StringBuilder sb = new StringBuilder();
        sb.append(gameState.getCurrentPlayerIndex()).append("*");
        sb.append(gameState.getTurnsToPlay()).append("*");
        sb.append(gameStarted).append("*");
        sb.append(expectedPlayers).append("*");

        String deckState = (deck != null && !deck.getCards().isEmpty())
                ? deck.getCards().stream()
                .map(c -> c.getType().name())
                .collect(Collectors.joining(","))
                : "WAITING_FOR_PLAYERS";
        sb.append(deckState).append("*");

        String playersState = gameState.getActivePlayers().stream()
                .map(p -> p.getAgentName() + "," + p.getNickname())
                .collect(Collectors.joining("|"));
        sb.append(playersState);

        String heartbeatState = clientsAliveRegister.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("|"));
        sb.append("*").append(heartbeatState);

        return sb.toString();
    }

    /**
     * Ricostruisce gameState e deck da una stringa serializzata.
     * Usato dal BackupMaster per aggiornare lo stato ricevuto via heartbeat.
     */
    protected void reconstructState(String data) {
        try {
            String[] parts = data.split("\\*", -1);
            if (parts.length < 6) return;

            if (gameState == null) gameState = new GameState();
            if (deck == null) deck = new Deck();

            try { gameState.setCurrentPlayerIndex(Integer.parseInt(parts[0])); }
            catch (NumberFormatException e) { gameState.setCurrentPlayerIndex(0); }

            try { gameState.setTurnsToPlay(Integer.parseInt(parts[1])); }
            catch (NumberFormatException e) { gameState.setTurnsToPlay(1); }

            try { gameStarted = Boolean.parseBoolean(parts[2]); }
            catch (Exception e) { gameStarted = false; }

            try { expectedPlayers = Integer.parseInt(parts[3]); }
            catch (NumberFormatException e) { expectedPlayers = -1; }

            List<Card> restoredCards = new ArrayList<>();
            if (!parts[4].isEmpty() && !parts[4].contains("WAITING")) {
                for (String cardName : parts[4].split(",")) {
                    String name = cardName.trim();
                    if (!name.isEmpty()) {
                        try { restoredCards.add(new Card(CardType.valueOf(name))); }
                        catch (Exception ignored) {}
                    }
                }
            }
            deck.setCards(restoredCards);

            List<Player> restoredPlayers = new ArrayList<>();
            if (parts.length > 5 && parts[5] != null && !parts[5].isEmpty()) {
                for (String pData : parts[5].split("\\|")) {
                    String[] pParts = pData.split(",");
                    if (pParts.length >= 2) {
                        restoredPlayers.add(new Player(pParts[0], pParts[1]));
                    }
                }
            }
            gameState.setActivePlayers(restoredPlayers);

            if (parts.length >= 7 && !parts[6].isEmpty()) {
                clientsAliveRegister.clear();
                for (String entry : parts[6].split("\\|")) {
                    String[] kv = entry.split("=");
                    if (kv.length == 2) {
                        try { clientsAliveRegister.put(kv[0], Long.parseLong(kv[1])); }
                        catch (NumberFormatException ignored) {}
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("[" + getLocalName() + "] Errore ricostruzione stato: " + e.getMessage());
        }
    }

    protected void broadcastToAll(String content) {
        for (Player p : gameState.getActivePlayers()) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(new AID(p.getAgentName(), true));
            msg.setContent(content);
            send(msg);
        }
    }

    protected void announceWinner() {
        Player winner = gameState.getWinner();
        if (winner != null) {
            broadcastToAll(Messages.WINNER + winner.getNickname());
            System.out.println("[" + getLocalName() + "] Vincitore: " + winner.getNickname());
        }
    }

    protected Player findPlayerByAgentName(String agentName) {
        return gameState.getActivePlayers().stream()
                .filter(p -> p.getAgentName().equals(agentName))
                .findFirst().orElse(null);
    }

    protected void notifyRemoveCard(AID target, String cardType) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(target);
        msg.setContent(Messages.REMOVE_CARD + cardType);
        send(msg);
    }

    protected void notifyRefresh(AID target) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(target);
        msg.setContent(Messages.REFRESH_HAND);
        send(msg);
    }

    protected void broadcastPlayersList() {
        String list = gameState.getActivePlayers().stream()
                .map(Player::getNickname)
                .collect(Collectors.joining(","));
        broadcastToAll(Messages.PLAYER_LIST + list);
    }

    private void sendDisconfirm(ACLMessage originalMsg, String reason) {
        ACLMessage reply = originalMsg.createReply();
        reply.setPerformative(ACLMessage.DISCONFIRM);
        reply.setContent(reason);
        send(reply);
    }

    protected void handlePlayerHeartbeat(ACLMessage msg) {
        clientsAliveRegister.put(msg.getSender().getName(), System.currentTimeMillis());
    }

    protected void checkDisconnectedPlayers() {
        if (gameState == null) return;
        long now = System.currentTimeMillis();

        List<Player> toRemove = gameState.getActivePlayers().stream()
                .filter(p -> {
                    Long last = clientsAliveRegister.get(p.getAgentName());
                    if (last == null) return false;
                    return now - last > PLAYER_TIMEOUT;
                })
                .toList();

        for (Player p : toRemove) {
            System.out.println("[" + getLocalName() + "] Player disconnesso: " + p.getNickname());

            boolean wasCurrent = gameState.getCurrentPlayer() != null &&
                    gameState.getCurrentPlayer().getAgentName().equals(p.getAgentName());

            gameState.removePlayer(p);
            clientsAliveRegister.remove(p.getAgentName());
            broadcastToAll(Messages.PLAYER_DISCONNECTED + p.getNickname());

            if (gameState.isGameOver()) {
                announceWinner();
                return;
            }
            if (wasCurrent) {
                gameState.nextTurn();
                nextTurn();
            }
        }
    }
}