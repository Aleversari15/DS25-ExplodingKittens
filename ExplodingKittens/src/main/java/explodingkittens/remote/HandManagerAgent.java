package explodingkittens.remote;

import explodingkittens.game.model.Card;
import explodingkittens.game.model.CardType;
import explodingkittens.game.model.Hand;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

/**
 * Agente che gestisce la mano di carte di un giocatore.
 */
public class HandManagerAgent extends Agent {
    private Hand hand;
    private AID playerAgentAID;

    @Override
    protected void setup() {
        hand = new Hand();
        Object[] args = getArguments();
        playerAgentAID = (AID) args[0];
        addBehaviour(new HandleMsgBehaviour());
    }

    /**
     * Comportamento ciclico che processa le richieste di gestione della mano.
     * Risponde a comandi per inizializzare la mano, aggiungere e rimuovere carte
     * o interrogare sulla presenza di un Defuse.
     */
    private class HandleMsgBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg != null) {
                String content = msg.getContent();
                if (content.startsWith(Messages.HAND_INIT)) {
                    initHand(content.substring(Messages.HAND_INIT.length()));

                } else if (content.startsWith(Messages.ADD_CARD)) {
                    CardType type = CardType.valueOf(content.substring(Messages.ADD_CARD.length()));
                    hand.addCard(new Card(type, type.name(), ""));
                    System.out.println("Carta aggiunta: " + type);

                } else if (content.startsWith(Messages.REMOVE_CARD)) {
                    CardType type = CardType.valueOf(content.substring(Messages.REMOVE_CARD.length()));
                    Card toRemove = hand.getCardOfType(type);
                    if (toRemove != null) hand.removeCard(toRemove);
                    System.out.println("Carta rimossa: " + type);

                } else if (content.equals(Messages.GET_HAND)) {
                    ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
                    reply.addReceiver(playerAgentAID);
                    reply.setContent(Messages.HAND_INIT + serializeHand());
                    send(reply);

                } else if (content.equals(Messages.HAS_DEFUSE_ASK)) {
                    boolean hasDefuse = hand.hasCardOfType(CardType.DEFUSE);
                    ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
                    reply.addReceiver(msg.getSender());
                    reply.setContent(hasDefuse ? Messages.HAS_DEFUSE_YES : Messages.HAS_DEFUSE_NO);
                    send(reply);

                } else if (content.equals(Messages.USE_DEFUSE)) {
                    Card defuse = hand.getCardOfType(CardType.DEFUSE);
                    if (defuse != null) {
                        hand.removeCard(defuse);
                    }
                }

            } else {
                block();
            }
        }

        /**
         * Popola la mano iniziale.
         * @param serialized stringa contenente i tipi di carta separati da virgola.
         */
        private void initHand(String serialized) {
            if (serialized == null || serialized.isEmpty()) return;
            String[] types = serialized.split(",");
            for (String t : types) {
                if (!t.isEmpty()) {
                    CardType type = CardType.valueOf(t.trim());
                    hand.addCard(new Card(type, type.name(), ""));
                }
            }
            ACLMessage ready = new ACLMessage(ACLMessage.INFORM);
            ready.addReceiver(playerAgentAID);
            ready.setContent(Messages.HAND_READY);
            send(ready);
        }
        /**
         * Serializza la mano in una stringa per la trasmissione.
         * @return una stringa rappresentante i tipi di carta.
         */
        private String serializeHand() {
            StringBuilder sb = new StringBuilder();
            for (Card c : hand.getCards()) {
                sb.append(c.getType().name()).append(",");
            }
            return sb.toString();
        }
    }
}
