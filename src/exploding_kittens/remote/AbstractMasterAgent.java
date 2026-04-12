package exploding_kittens.remote;

import exploding_kittens.game.model.*;
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
    protected static final String CAT_LOG = "[Master - CAT_CARD] "; //TODO da rimuovere al termine dei test

    /**
     * Processa un messaggio di gioco. Restituisce true se il messaggio
     * è stato gestito, false se non era un messaggio di gioco.
     */
    protected boolean processGameMessage(ACLMessage msg) {
        if (msg == null || msg.getContent() == null) return false;
        String content = msg.getContent();

        // HAND_RESPONSE (risposta alla richiesta di validazione mano)
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

        // PLAYER_ELIMINATED (gestito indipendentemente dal turno)
        if (content.equals(Messages.PLAYER_ELIMINATED)) {
            handleElimination(msg);
            return true;
        }

        // Solo REQUEST per le azioni normali
        if (msg.getPerformative() != ACLMessage.REQUEST) return false;

        // Verifica del turno e gestione degli altri messaggi (validi solo all'interno del turno)
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


    private void processActionWithHand(ACLMessage originalMsg, String serializedHand) {
        String content = originalMsg.getContent();

        List<CardType> hand = Arrays.stream(serializedHand.split(","))
                .filter(s -> !s.isEmpty())
                .map(CardType::valueOf)
                .collect(Collectors.toList());

        if (content.startsWith(Messages.PLAY)) {
            CardType type = CardType.valueOf(content.substring(Messages.PLAY.length()));

            if (!hand.contains(type)) {
                sendDisconfirm(originalMsg, Messages.CARD_NOT_IN_HAND);
                return;
            }

            notifyRemoveCard(originalMsg.getSender(), type.name());

            switch (type) {
                case SKIP           -> handleSkip(originalMsg);
                case ATTACK         -> handleAttack(originalMsg);
                case SHUFFLE        -> handleShuffle(originalMsg);
                case SEE_THE_FUTURE -> handleSeeTheFuture(originalMsg);
                case CAT_CARD       -> prepareCatCard(originalMsg);
                default             -> sendDisconfirm(originalMsg, Messages.CARD_NOT_IN_HAND);
            }

        } else if (content.startsWith(Messages.CAT_CARD_PLAY)) {
            long catCount = hand.stream().filter(t -> t == CardType.CAT_CARD).count();
            if (catCount < 2) {
                sendDisconfirm(originalMsg, Messages.CARD_NOT_IN_HAND);
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

    //Gestione delle singole carte
    protected void handleDraw(ACLMessage msg) {
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
        gameState.nextTurn();
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
        broadcastToAll(msg.getSender().getLocalName() + " ha neutralizzato l'Exploding Kitten!");
        gameState.nextTurn();
        nextTurn();
    }

    protected void handleElimination(ACLMessage msg) {
        Player eliminated = findPlayerByAgentName(msg.getSender().getName());
        if (eliminated != null) {
            gameState.removePlayer(eliminated);
            broadcastToAll(eliminated.getNickname() + " e' stato eliminato!");
            System.out.println(eliminated.getNickname() + " eliminato.");
        }
        if (gameState.isGameOver()) {
            announceWinner();
        } else {
            nextTurn();
        }
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
        sb.append(gameState.getCurrentPlayerIndex()).append(":");
        sb.append(gameState.getTurnsToPlay()).append(":");

        String deckState = (deck != null && !deck.getCards().isEmpty())
                ? deck.getCards().stream().map(c -> c.getType().name()).collect(Collectors.joining(","))
                : "WAITING_FOR_PLAYERS";
        sb.append(deckState).append(":");

        String playersState = gameState.getActivePlayers().stream()
                .map(p -> p.getAgentName() + "," + p.getNickname())
                .collect(Collectors.joining("|"));
        sb.append(playersState);

        return sb.toString();
    }

    /**
     * Ricostruisce gameState e deck da una stringa serializzata.
     * Usato dal BackupMaster per aggiornare lo stato ricevuto via heartbeat.
     */
    protected void reconstructState(String data) {
        try {
            String[] parts = data.split(":", 4);
            if (parts.length < 4) return;

            if (gameState == null) gameState = new GameState();
            if (deck == null)      deck      = new Deck();

            try { gameState.setCurrentPlayerIndex(Integer.parseInt(parts[0])); }
            catch (NumberFormatException e) { gameState.setCurrentPlayerIndex(0); }

            try { gameState.setTurnsToPlay(Integer.parseInt(parts[1])); }
            catch (NumberFormatException e) { gameState.setTurnsToPlay(1); }

            List<Card> restoredCards = new ArrayList<>();
            if (!parts[2].isEmpty() && !parts[2].contains("WAITING")) {
                for (String cardName : parts[2].split(",")) {
                    String name = cardName.trim();
                    if (!name.isEmpty()) {
                        try { restoredCards.add(new Card(CardType.valueOf(name))); }
                        catch (Exception ignored) {}
                    }
                }
            }
            deck.setCards(restoredCards);

            if (!parts[3].isEmpty() && !parts[3].equals("null")) {
                List<Player> restoredPlayers = new ArrayList<>();
                for (String pData : parts[3].split("\\|")) {
                    String[] pParts = pData.split(",");
                    if (pParts.length >= 2) {
                        String nick = pParts[1].replace("Player_", "");
                        restoredPlayers.add(new Player(pParts[0], nick));
                    }
                }
                if (!restoredPlayers.isEmpty()) gameState.setActivePlayers(restoredPlayers);
            }

        } catch (Exception e) {
            System.err.println("Errore ricostruzione stato: " + e.getMessage());
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
            System.out.println("Vincitore: " + winner.getNickname());
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
        broadcastToAll("PLAYERS_LIST:" + list);
    }

    private void sendDisconfirm(ACLMessage originalMsg, String reason) {
        ACLMessage reply = originalMsg.createReply();
        reply.setPerformative(ACLMessage.DISCONFIRM);
        reply.setContent(reason);
        send(reply);
    }
}
