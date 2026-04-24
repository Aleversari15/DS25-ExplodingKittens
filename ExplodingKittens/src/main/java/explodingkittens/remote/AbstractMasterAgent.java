package explodingkittens.remote;

import explodingkittens.game.model.*;
import jade.core.AID;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Classe base che contiene tutta la logica di gioco condivisa
 * tra GameMasterAgent e BackupMasterAgent.
 * Gestisce azioni delle carte, Cat Card, eliminazioni, turni,
 * serializzazione stato, broadcast ai giocatori.
 * Le sottoclassi implementano solo la logica specifica del loro ruolo
 * (setup, heartbeat, promozione, lobby).
 */
public abstract class AbstractMasterAgent extends Agent {
    protected GameState gameState;
    protected Deck      deck;
    protected ACLMessage pendingAction    = null;
    protected String     pendingCatTarget = null;
    protected Map<String, Long> clientsAliveRegister = new HashMap<>();
    protected static final long PLAYER_TIMEOUT = 10000;
    protected boolean gameStarted = false;
    protected int expectedPlayers = -1;

    /**
     * Processa i messaggi in arrivo smistandoli in messaggi che non dipendono dal turno
     * e azioni di gioco.
     */
    protected boolean processGameMessage(ACLMessage msg) {
        if (msg == null || msg.getContent() == null) return false;
        String content = msg.getContent();
        Player current = gameState.getCurrentPlayer();
        if (current == null) return false;

        if (content.equals(Messages.HEARTBEAT_CLIENT)) {
            handlePlayerHeartbeat(msg);
            return true;
        }
        if (content.equals(Messages.HAND_INIT)){
            gameStarted= true;
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
            return true;
        }
        if (content.equals(Messages.PLAYER_ELIMINATED)) { //gestito indipendentemente dal turno
            handleElimination(msg);
            return true;
        }
        if (!msg.getSender().getName().equals(current.getAgentName())) {
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.DISCONFIRM);
            reply.setContent(Messages.NOT_YOUR_TURN);
            send(reply);
            return true;
        }
        if (content.equals(Messages.DRAW)) {
            handleDraw(msg);
        } else if (content.startsWith(Messages.PLAY) || content.startsWith(Messages.CAT_CARD_PLAY)) {
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

    /**
     * Inizializza e avvia la partita:
     * - costruisce il mazzo
     * - distribuisce le carte iniziali
     * - inserisce gli Exploding Kittens
     * - notifica i giocatori
     */
    protected void setupAndStartGame() {
        if (gameStarted) return; //TODO Serve ancora?
        this.gameStarted = true;
        if (deck == null || deck.size() == 0) {
            deck = DeckBuilder.prepareBaseDeck(expectedPlayers);
            Map<String, String> hands = DeckBuilder.buildPlayerHands(deck, gameState.getActivePlayers());
            DeckBuilder.insertExplodingKittens(deck, expectedPlayers);

            for (Player player : gameState.getActivePlayers()) {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(new AID(player.getAgentName(), true));
                msg.setContent(Messages.HAND_INIT + hands.get(player.getAgentName()));
                send(msg);
            }
        }

        System.out.println("Partita avviata da " + getLocalName());
        broadcastPlayersList();
        nextTurn();
    }

    /**
     * Processa un'azione del giocatore utilizzando la sua mano aggiornata.
     * @param originalMsg messaggio originale del giocatore
     * @param serializedHand mano del giocatore serializzata
     */
    private void processActionWithHand(ACLMessage originalMsg, String serializedHand) {
        String content = originalMsg.getContent();

        List<CardType> hand = Arrays.stream(serializedHand.split(","))
                .filter(s -> !s.isEmpty())
                .map(CardType::valueOf)
                .collect(Collectors.toList());

        if (content.startsWith(Messages.PLAY)) {
            CardType type = CardType.valueOf(content.substring(Messages.PLAY.length()));

            if (!hand.contains(type)) {
                sendDisconfirm(originalMsg, Messages.TWO_CAT_NOT_IN_HAND); //TODO Serve ancora??
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
            long catCount = hand.stream().filter(t -> t == CardType.CAT_CARD).count();
            if (catCount < 2) {
                sendDisconfirm(originalMsg, Messages.TWO_CAT_NOT_IN_HAND);
                return;
            }
            prepareCatCard(originalMsg);
        }
    }

    /**
     * Prepara l'esecuzione della Cat Card:
     * - valida il target
     * - salva lo stato pending
     * - richiede la mano del target
     *
     * @param originalMsg messaggio originale del giocatore
     */
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

        pendingAction    = originalMsg;
        pendingCatTarget = target.getAgentName();

        ACLMessage query = new ACLMessage(ACLMessage.REQUEST);
        query.addReceiver(new AID(target.getAgentName(), true));
        query.setContent(Messages.REQUEST_HAND);
        send(query);
    }

    /**
     * Completa l'azione Cat Card una volta ricevuta la mano del target:
     * - seleziona una carta casuale
     * - la trasferisce al giocatore attivo
     * @param originalMsg messaggio del giocatore che ha giocato la carta
     * @param serializedTargetHand mano del bersaglio
     */
    private void processCatCardWithTargetHand(ACLMessage originalMsg, String serializedTargetHand) {
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

    /**
     * Gestisce l'azione di pesca di una carta dal mazzo.
     * @param msg messaggio del giocatore
     */
    protected void handleDraw(ACLMessage msg) {
        Card drawn = deck.removeTopCard();

        if (drawn.getType() == CardType.EXPLODING_KITTEN) {
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            reply.setContent(Messages.DREW_KITTEN + ":"+ deck.size());
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

    /**
     * Gestisce l'azione SKIP.
     */
    protected void handleSkip(ACLMessage msg) {
        notifyRefresh(msg.getSender());
        ACLMessage reply = msg.createReply();
        reply.setPerformative(ACLMessage.CONFIRM);
        reply.setContent(Messages.SKIP_OK);
        send(reply);
        gameState.nextTurn();
        nextTurn();
    }

    /**
     * Gestisce l'azione ATTACK (il prossimo giocatore gioca due turni).
     */
    protected void handleAttack(ACLMessage msg) {
        notifyRefresh(msg.getSender());
        ACLMessage reply = msg.createReply();
        reply.setPerformative(ACLMessage.CONFIRM);
        reply.setContent(Messages.ATTACK_OK);
        send(reply);

        Player current = gameState.getCurrentPlayer();
        do {
            gameState.nextTurn();
        } while (current == gameState.getCurrentPlayer());
        gameState.setTurnsToPlay(2);
        nextTurn();
    }

    /**
     * Mescola il mazzo.
     */
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

    /**
     * Mostra le prime 3 carte del mazzo al giocatore.
     */
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

    /**
     * Gestisce l'utilizzo della carta Defuse.
     * Reinserisce l'Exploding Kitten nel mazzo.
     */
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
        broadcastToAll(Messages.SHOW_DEFUSE_USED+msg.getSender().getLocalName() );
        gameState.nextTurn();
        nextTurn();
    }

    /**
     * Gestisce l'eliminazione di un giocatore:
     * - rimozione dallo stato di gioco
     * - aggiornamento lista player
     * - verifica fine partita
     */
    protected void handleElimination(ACLMessage msg) {
        Player eliminated = findPlayerByAgentName(msg.getSender().getName());
        if (eliminated != null) {
            gameState.removePlayer(eliminated);
            broadcastToAll(eliminated.getNickname() + " e' stato eliminato!");
            broadcastPlayersList();
            System.out.println(eliminated.getNickname() + " eliminato.");
        }

        if (gameState.isGameOver()) { announceWinner();}
        else { nextTurn(); }
    }

    /**
     * Notifica tutti i giocatori del turno corrente.
     * Chiamato dopo ogni azione che termina il turno.
     */
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
     * Metodo per la serializzazione dello stato del gioco.
     * Utilizzato per aggiornare il BackupMasterAgent sullo stato del gioco.
     * @return stato del gioco sotto forma di stringa.
     */
    protected String serializeState() {
        StringBuilder sb = new StringBuilder();
        sb.append(gameState.getCurrentPlayerIndex()).append("*");
        sb.append(gameState.getTurnsToPlay()).append("*");
        sb.append(gameStarted).append("*");
        sb.append(expectedPlayers).append("*");

        String deckState = (deck != null && !deck.getCards().isEmpty())
                ? deck.getCards().stream().map(c -> c.getType().name()).collect(Collectors.joining(","))
                : "WAITING_FOR_PLAYERS";
        sb.append(deckState).append("*");

        String playersState = gameState.getActivePlayers().stream()
                .map(p -> p.getAgentName() + "," + p.getNickname())
                .collect(Collectors.joining("|"));
        sb.append(playersState);

        for(Player p : gameState.getActivePlayers()){
            System.out.println("SERIALIZE PLAYER: " + p.getAgentName() + "," + p.getNickname());
        }

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
            if (deck == null)      deck      = new Deck();

            try { gameState.setCurrentPlayerIndex(Integer.parseInt(parts[0])); }
            catch (NumberFormatException e) { gameState.setCurrentPlayerIndex(0); }

            try { gameState.setTurnsToPlay(Integer.parseInt(parts[1])); }
            catch (NumberFormatException e) { gameState.setTurnsToPlay(1); }

            try{this.gameStarted = Boolean.parseBoolean(parts[2]);}
            catch (Exception e){ this.gameStarted = false;}

            try { this.expectedPlayers = Integer.parseInt(parts[3]); }
            catch (NumberFormatException e) { this.expectedPlayers = -1; }

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
            System.out.println("Parte 5 serializzazione stato" + parts[5]);
            if (parts.length > 5 && parts[5] != null && !parts[5].isEmpty()) {

                for (String pData : parts[5].split("\\|")) {
                    String[] pParts = pData.split(",");
                    if (pParts.length >= 2) {
                        String nick = pParts[1].replace("Player_", "");
                        restoredPlayers.add(new Player(pParts[0], nick));
                    }
                }
                /*if (!restoredPlayers.isEmpty()) */
            }
            System.out.println("Players Restored: " +restoredPlayers);
            gameState.setActivePlayers(restoredPlayers);

            if (parts.length >= 6) {
                clientsAliveRegister.clear();
                for (String entry : parts[6].split("\\|")) {
                    String[] kv = entry.split("=");
                    if (kv.length == 2) {
                        clientsAliveRegister.put(kv[0], Long.parseLong(kv[1]));
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Errore ricostruzione stato: " + e.getMessage());
        }
    }

    /**
     * Invia un messaggio a tutti i giocatori attivi.
     */
    protected void broadcastToAll(String content) {
        for (Player p : gameState.getActivePlayers()) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(new AID(p.getAgentName(), true));
            msg.setContent(content);
            send(msg);
        }
    }

    /**
     * Annuncia il vincitore della partita.
     */
    protected void announceWinner() {
        Player winner = gameState.getWinner();
        System.out.println("Vincitore in announce Winner:" + winner.getNickname());
        if (winner != null) {
            broadcastToAll(Messages.WINNER + winner.getNickname());
            System.out.println("Vincitore: " + winner.getNickname());
        }
    }

    /**
     * Trova un player tramite il nome dell'agente.
     */
    protected Player findPlayerByAgentName(String agentName) {
        return gameState.getActivePlayers().stream()
                .filter(p -> p.getAgentName().equals(agentName))
                .findFirst().orElse(null);
    }

    /**
     * Notifica la rimozione di una carta a un player.
     */
    protected void notifyRemoveCard(AID target, String cardType) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(target);
        msg.setContent(Messages.REMOVE_CARD + cardType);
        send(msg);
    }

    /**
     * Richiede al client di aggiornare la propria mano.
     */
    protected void notifyRefresh(AID target) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(target);
        msg.setContent(Messages.REFRESH_HAND);
        send(msg);
    }

    /**
     * Invia la lista aggiornata dei giocatori attivi.
     */
    protected void broadcastPlayersList() {
        String list = gameState.getActivePlayers().stream()
                .map(Player::getNickname)
                .collect(Collectors.joining(","));
        broadcastToAll(Messages.PLAYER_LIST + list);
    }

    /**
     * Invia un messaggio di errore (DISCONFIRM) al client.
     */
    private void sendDisconfirm(ACLMessage originalMsg, String reason) {
        ACLMessage reply = originalMsg.createReply();
        reply.setPerformative(ACLMessage.DISCONFIRM);
        reply.setContent(reason);
        send(reply);
    }

    /**
     * Registra l'heartbeat ricevuto da un client.
     */
    protected void handlePlayerHeartbeat(ACLMessage msg) {
        clientsAliveRegister.put(msg.getSender().getName(), System.currentTimeMillis());
    }

    /**
     * Metodo per controllare l'attività dei client.
     * Se non riceviamo heartbeats da un tempo > PLAYER_TIMEOUT allora eliminiamo il player dalla partita.
     * Se il player che non risulta più attivo è proprio quello di turno, lo eliminiamo e passiamo al turno del prossimo giocatore.
     */
    protected void checkDisconnectedPlayers() {
        long now = System.currentTimeMillis();

        List<Player> toRemove = gameState.getActivePlayers().stream()
                .filter(p -> {
                    Long last = clientsAliveRegister.get(p.getAgentName());
                    System.out.println("[CHECK TIMEOUT] " + p.getNickname() + " last heartbeat: " + (last != null ? (now - last) + "ms ago" : "never"));
                    if (last == null) return false;
                    return  now - last > PLAYER_TIMEOUT;
                })
                .toList();

        for (Player p : toRemove) {
            System.out.println("[FAULT] Player disconnesso: " + p.getNickname());

            boolean wasCurrent = gameState.getCurrentPlayer() != null &&
                    gameState.getCurrentPlayer().getAgentName().equals(p.getAgentName());

            gameState.removePlayer(p);
            clientsAliveRegister.remove(p.getAgentName());
            System.out.println("[DEBUG] Giocatori rimasti: " + gameState.getActivePlayers().size());
            broadcastToAll(Messages.PLAYER_DISCONNECTED + p.getNickname());

            if (gameState.isGameOver() ) {
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
