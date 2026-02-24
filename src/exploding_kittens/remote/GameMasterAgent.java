package exploding_kittens.remote;

import exploding_kittens.game.model.*;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.*;

public class GameMasterAgent extends Agent {

    private GameState gameState;
    private Deck deck;
    private Map<String, Hand> playerHands;  // agentName → Hand
    private int expectedPlayers;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        expectedPlayers = (args != null) ? Integer.parseInt(args[0].toString()) : 2;

        gameState   = new GameState();
        deck        = new Deck();
        playerHands = new HashMap<>();

        System.out.println("GameMaster avviato, aspetto " + expectedPlayers + " giocatori...");
        addBehaviour(new WaitForPlayersBehaviour());
    }

    //Aspetta i giocatori
    private class WaitForPlayersBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null && msg.getContent().equals(Messages.JOIN)) {
                String playerName = msg.getSender().getName();
                Player player = new Player(playerName, msg.getSender().getLocalName());
                gameState.addPlayer(player);
                playerHands.put(playerName, new Hand());

                System.out.println("Giocatore registrato: " + playerName);

                // Conferma al giocatore
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.CONFIRM);
                reply.setContent(Messages.JOINED);
                send(reply);

                // Se abbiamo tutti i giocatori, avvia la partita
                if (gameState.getActivePlayers().size() == expectedPlayers) {
                    myAgent.removeBehaviour(this);
                    addBehaviour(new StartGameBehaviour());
                }
            } else {
                block();
            }
        }
    }

    //Distribuisce carte
    private class StartGameBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            // Crea il mazzo senza Exploding Kittens e senza Defuse, poi mischia
            List<Card> cards = CardFactory.createStandardDeck(expectedPlayers);
            cards.removeIf(c -> c.getType() == CardType.EXPLODING_KITTEN);
            cards.removeIf(c -> c.getType() == CardType.DEFUSE);
            Collections.shuffle(cards);
            deck.setCards(cards);

            // Distribuisci 1 Defuse + 4 carte a ciascun giocatore
            for (Player player : gameState.getActivePlayers()) {
                Hand hand = playerHands.get(player.getAgentName());
                hand.addCard(new Card(CardType.DEFUSE, "Defuse", "Neutralizes an Exploding Kitten."));
                for (int i = 0; i < 4; i++) {
                    hand.addCard(deck.removeTopCard());
                }

                // Invia la mano iniziale al PlayerAgent
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(new AID(player.getAgentName(), true));
                msg.setContent(Messages.HAND_INIT + serializeHand(hand));
                send(msg);
            }

            // Reinserisci gli Exploding Kittens nel mazzo in posizioni casuali
            for (int i = 0; i < expectedPlayers - 1; i++) {
                deck.insertCard(
                        new Card(CardType.EXPLODING_KITTEN, "Exploding Kitten", "You explode!"),
                        new Random().nextInt(deck.size() + 1)
                );
            }

            System.out.println("Partita avviata!");
            addBehaviour(new ManageTurnBehaviour());
        }
    }

    //Notifica giocatore di turno
    private class ManageTurnBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            if (gameState.isGameOver()) {
                announceWinner();
                return;
            }

            Player current = gameState.getCurrentPlayer();
            System.out.println("Turno di: " + current.getNickname());

            // Notifica tutti i giocatori
            for (Player p : gameState.getActivePlayers()) {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(new AID(p.getAgentName(), true));

                if (p.getAgentName().equals(current.getAgentName())) {
                    msg.setContent(Messages.YOUR_TURN);
                } else {
                    msg.setContent(Messages.TURN_OF + current.getNickname());
                }
                send(msg);
            }

            addBehaviour(new HandleActionBehaviour());
        }
    }

    //Gestisce azioni giocatore
    private class HandleActionBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String content   = msg.getContent();
                String senderName = msg.getSender().getName();
                Player current   = gameState.getCurrentPlayer();

                // Verifica che sia il turno del mittente
                if (!senderName.equals(current.getAgentName())) {
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.DISCONFIRM);
                    reply.setContent(Messages.NOT_YOUR_TURN);
                    send(reply);
                    return;
                }

                if (content.equals(Messages.DRAW)) {
                    handleDraw(msg);
                } else if (content.startsWith(Messages.PLAY)) {
                    handlePlayCard(msg, content.substring(Messages.PLAY.length()));
                } else if (content.startsWith(Messages.DEFUSE_PLAY)) {
                    handleDefuse(msg);
                } else if (content.equals(Messages.PLAYER_ELIMINATED)) {
                    handleElimination(msg);
                }

            } else {
                block();
            }
        }

        private void handleDraw(ACLMessage msg) {
            Card drawn = deck.removeTopCard();
            Hand hand  = playerHands.get(msg.getSender().getName());

            if (drawn.getType() == CardType.EXPLODING_KITTEN) {
                // Informa il giocatore che ha pescato un Kitten
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setContent(Messages.DREW_KITTEN);
                send(reply);
                // Attende la risposta (DEFUSE o PLAYER_ELIMINATED)

            } else {
                hand.addCard(drawn);

                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setContent(Messages.DREW + drawn.getType().name());
                send(reply);

                // Fine turno
                gameState.nextTurn();
                myAgent.removeBehaviour(this);
                addBehaviour(new ManageTurnBehaviour());
            }
        }

        private void handlePlayCard(ACLMessage msg, String cardTypeName) {
            CardType type = CardType.valueOf(cardTypeName);
            Hand hand     = playerHands.get(msg.getSender().getName());

            if (!hand.hasCardOfType(type)) {
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.DISCONFIRM);
                reply.setContent(Messages.CARD_NOT_IN_HAND);
                send(reply);
                return;
            }

            hand.removeCard(hand.getCardOfType(type));

            switch (type) {
                case SKIP          -> handleSkip(msg);
                case ATTACK        -> handleAttack(msg);
                case SHUFFLE       -> handleShuffle(msg);
                case SEE_THE_FUTURE -> handleSeeTheFuture(msg);
                case CAT_CARD      -> handleCatCard(msg);
                default -> {
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.DISCONFIRM);
                    reply.setContent(Messages.CARD_NOT_IN_HAND);
                    send(reply);
                }
            }
        }

        private void handleSkip(ACLMessage msg) {
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent(Messages.SKIP_OK);
            send(reply);

            gameState.nextTurn();
            myAgent.removeBehaviour(this);
            addBehaviour(new ManageTurnBehaviour());
        }

        private void handleAttack(ACLMessage msg) {
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

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent(Messages.SHUFFLE_OK);
            send(reply);
        }

        private void handleSeeTheFuture(ACLMessage msg) {
            List<Card> top3 = deck.peekTop(3);
            StringBuilder sb = new StringBuilder(Messages.SEE_THE_FUTURE);
            for (Card c : top3) sb.append(c.getType().name()).append(",");

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            reply.setContent(sb.toString());
            send(reply);
            // See the Future non termina il turno
        }

        private void handleDefuse(ACLMessage msg) {
            // Contenuto atteso: "DEFUSE:<position>"
            String[] parts   = msg.getContent().split(":");
            int position     = Integer.parseInt(parts[1]);
            position         = Math.min(position, deck.size());

            deck.insertCard(
                    new Card(CardType.EXPLODING_KITTEN, "Exploding Kitten", "You explode!"),
                    position
            );

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent(Messages.DEFUSED);
            send(reply);

            broadcastToAll(msg.getSender().getLocalName() + " ha defusato il Kitten!");

            gameState.nextTurn();
            myAgent.removeBehaviour(this);
            addBehaviour(new ManageTurnBehaviour());
        }

        private void handleCatCard(ACLMessage msg) {
            // Contenuto atteso: "CAT_CARD:<targetAgentName>"
            String[] parts = msg.getContent().split(":");
            if (parts.length < 2) {
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.DISCONFIRM);
                reply.setContent(Messages.MISSING_TARGET);
                send(reply);
                return;
            }

            String targetName = parts[1];
            Hand targetHand   = playerHands.get(targetName);

            if (targetHand == null || targetHand.size() == 0) {
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.DISCONFIRM);
                reply.setContent(Messages.INVALID_TARGET);
                send(reply);
                return;
            }

            // Ruba una carta casuale dal target
            int randomIndex = new Random().nextInt(targetHand.size());
            Card stolen     = targetHand.getCards().get(randomIndex);
            targetHand.removeCard(stolen);
            playerHands.get(msg.getSender().getName()).addCard(stolen);

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent(Messages.STOLEN + stolen.getType().name());
            send(reply);

            // Notifica il target della carta rubata
            ACLMessage notifyTarget = new ACLMessage(ACLMessage.INFORM);
            notifyTarget.addReceiver(new AID(targetName, true));
            notifyTarget.setContent(Messages.STOLEN + stolen.getType().name());
            send(notifyTarget);
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

    private String serializeHand(Hand hand) {
        StringBuilder sb = new StringBuilder();
        for (Card c : hand.getCards()) {
            sb.append(c.getType().name()).append(",");
        }
        return sb.toString();
    }
}