package exploding_kittens.remote;

import exploding_kittens.game.model.*;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.*;

public class GameMasterAgent extends Agent {

    private GameState gameState;
    private Deck deck;
    private Map<String, Hand> playerHands;
    private int expectedPlayers;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        expectedPlayers = (args != null) ? Integer.parseInt(args[0].toString()) : 2;

        gameState = new GameState();
        deck = new Deck();
        playerHands = new HashMap<>();

        System.out.println("GameMaster avviato, aspetto " + expectedPlayers + " giocatori...");

        addBehaviour(new WaitForPlayersBehaviour());
    }

   //Aspetta i giocatori per iniziare il gioco
    private class WaitForPlayersBehaviour extends CyclicBehaviour {

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null && msg.getContent().equals("JOIN")) {
                String playerName = msg.getSender().getName();
                Player player = new Player(playerName, msg.getSender().getLocalName());
                gameState.addPlayer(player);
                playerHands.put(playerName, new Hand());

                System.out.println("Giocatore registrato: " + playerName);

                // Conferma al giocatore
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.CONFIRM);
                reply.setContent("JOINED");
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

   //Distribuisce le carte
    private class StartGameBehaviour extends OneShotBehaviour {

        @Override
        public void action() {
            // Crea e mischia il mazzo (senza Exploding Kittens e senza Defuse)
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

                // Invia la mano al PlayerAgent
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(new jade.core.AID(player.getAgentName(), true));
                msg.setContent("HAND:" + serializeHand(hand));
                send(msg);
            }

            // Reinserisci gli Exploding Kittens nel mazzo
            for (int i = 0; i < expectedPlayers - 1; i++) {
                deck.insertCard(
                        new Card(CardType.EXPLODING_KITTEN, "Exploding Kitten", "You explode!"),
                        new Random().nextInt(deck.size())
                );
            }

            System.out.println("Partita avviata!");
            addBehaviour(new ManageTurnBehaviour());
        }
    }

  //Gestisce turno
    private class ManageTurnBehaviour extends OneShotBehaviour {

        @Override
        public void action() {
            if (gameState.isGameOver()) {
                announceWinner();
                return;
            }

            Player current = gameState.getCurrentPlayer();
            System.out.println("Turno di: " + current.getNickname());

            // Notifica tutti i giocatori del turno corrente
            for (Player p : gameState.getActivePlayers()) {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(new jade.core.AID(p.getAgentName(), true));
                if (p.getAgentName().equals(current.getAgentName())) {
                    msg.setContent("YOUR_TURN");
                } else {
                    msg.setContent("TURN:" + current.getNickname());
                }
                send(msg);
            }

            addBehaviour(new HandleActionBehaviour());
        }
    }


    //Gestisce azioni del giocatore di turno
    private class HandleActionBehaviour extends CyclicBehaviour {

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String content = msg.getContent();
                String senderName = msg.getSender().getName();
                Player current = gameState.getCurrentPlayer();

                // Verifica che sia il turno del mittente
                if (!senderName.equals(current.getAgentName())) {
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.DISCONFIRM);
                    reply.setContent("NOT_YOUR_TURN");
                    send(reply);
                    return;
                }

                if (content.equals("DRAW")) {
                    handleDraw(msg);
                } else if (content.startsWith("PLAY:")) {
                    handlePlayCard(msg, content.substring(5));
                }
            } else {
                block();
            }
        }

        private void handleDraw(ACLMessage msg) {
            Card drawn = deck.removeTopCard();
            Hand hand = playerHands.get(msg.getSender().getName());

            if (drawn.getType() == CardType.EXPLODING_KITTEN) {
                // Informa il giocatore che ha pescato un Kitten
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setContent("DREW:EXPLODING_KITTEN");
                send(reply);
                // La risposta (Defuse o eliminazione) arriverà con un nuovo messaggio
            } else {
                hand.addCard(drawn);
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setContent("DREW:" + drawn.getType().name());
                send(reply);

                // Fine turno
                gameState.nextTurn();
                myAgent.removeBehaviour(this);
                addBehaviour(new ManageTurnBehaviour());
            }
        }

        private void handlePlayCard(ACLMessage msg, String cardTypeName) {
            CardType type = CardType.valueOf(cardTypeName);
            Hand hand = playerHands.get(msg.getSender().getName());

            // Verifica che il giocatore abbia la carta
            if (!hand.hasCardOfType(type)) {
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.DISCONFIRM);
                reply.setContent("CARD_NOT_IN_HAND");
                send(reply);
                return;
            }

            hand.removeCard(hand.getCardOfType(type));

            switch (type) {
                case SKIP -> handleSkip(msg);
                case ATTACK -> handleAttack(msg);
                case SHUFFLE -> handleShuffle(msg);
                case SEE_THE_FUTURE -> handleSeeTheFuture(msg);
                case DEFUSE -> handleDefuse(msg);
                case CAT_CARD -> handleCatCard(msg);
                default -> {
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.DISCONFIRM);
                    reply.setContent("INVALID_CARD");
                    send(reply);
                }
            }
        }

        private void handleSkip(ACLMessage msg) {
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent("SKIP_OK");
            send(reply);

            gameState.nextTurn();
            myAgent.removeBehaviour(this);
            addBehaviour(new ManageTurnBehaviour());
        }

        private void handleAttack(ACLMessage msg) {
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent("ATTACK_OK");
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
            reply.setContent("SHUFFLE_OK");
            send(reply);
        }

        private void handleSeeTheFuture(ACLMessage msg) {
            List<Card> top3 = deck.peekTop(3);
            StringBuilder sb = new StringBuilder("SEE_THE_FUTURE:");
            for (Card c : top3) sb.append(c.getType().name()).append(",");

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            reply.setContent(sb.toString());
            send(reply);
        }

        private void handleDefuse(ACLMessage msg) {
            // Il giocatore ha usato il Defuse dopo aver pescato un Kitten
            // Il contenuto include la posizione dove reinserire: "DEFUSE:3"
            String[] parts = msg.getContent().split(":");
            int position = Integer.parseInt(parts[1]);
            position = Math.min(position, deck.size());

            deck.insertCard(
                    new Card(CardType.EXPLODING_KITTEN, "Exploding Kitten", "You explode!"),
                    position
            );

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent("DEFUSED");
            send(reply);

            // Notifica tutti
            broadcastToAll("INFORM", msg.getSender().getLocalName() + " ha defusato il Kitten!");

            gameState.nextTurn();
            myAgent.removeBehaviour(this);
            addBehaviour(new ManageTurnBehaviour());
        }

        private void handleCatCard(ACLMessage msg) {
            // Il contenuto include il target: "CAT_CARD:targetAgentName"
            String[] parts = msg.getContent().split(":");
            if (parts.length < 2) {
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.DISCONFIRM);
                reply.setContent("MISSING_TARGET");
                send(reply);
                return;
            }

            String targetName = parts[1];
            Hand targetHand = playerHands.get(targetName);

            if (targetHand == null || targetHand.size() == 0) {
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.DISCONFIRM);
                reply.setContent("INVALID_TARGET");
                send(reply);
                return;
            }

            // Ruba carta casuale
            int randomIndex = new Random().nextInt(targetHand.size());
            Card stolen = targetHand.getCards().get(randomIndex);
            targetHand.removeCard(stolen);
            playerHands.get(msg.getSender().getName()).addCard(stolen);

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.CONFIRM);
            reply.setContent("STOLEN:" + stolen.getType().name());
            send(reply);
        }
    }


    private void broadcastToAll(String performative, String content) {
        int perf = performative.equals("INFORM") ? ACLMessage.INFORM : ACLMessage.INFORM;
        for (Player p : gameState.getActivePlayers()) {
            ACLMessage msg = new ACLMessage(perf);
            msg.addReceiver(new jade.core.AID(p.getAgentName(), true));
            msg.setContent(content);
            send(msg);
        }
    }

    private void announceWinner() {
        Player winner = gameState.getWinner();
        broadcastToAll("INFORM", "WINNER:" + winner.getNickname());
        System.out.println("Vincitore: " + winner.getNickname());
    }

    private String serializeHand(Hand hand) {
        StringBuilder sb = new StringBuilder();
        for (Card c : hand.getCards()) {
            sb.append(c.getType().name()).append(",");
        }
        return sb.toString();
    }
}
