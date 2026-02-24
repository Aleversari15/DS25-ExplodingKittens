package exploding_kittens.remote;

import exploding_kittens.game.model.Card;
import exploding_kittens.game.model.CardType;
import exploding_kittens.game.model.Hand;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

public class HandManagerAgent extends Agent {

    private Hand hand;
    private AID playerAgentAID;

    @Override
    protected void setup() {
        hand = new Hand();
        Object[] args = getArguments();
        playerAgentAID = (AID) args[0];

        System.out.println("HandManagerAgent avviato per: " + playerAgentAID.getLocalName());
        addBehaviour(new HandleMsgBehaviour());
    }


    private class HandleMsgBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();

            if (msg != null) {
                String content = msg.getContent();

                if (content.startsWith(Messages.HAND_INIT)) {
                    // Inizializza la mano con le carte ricevute
                    initHand(content.substring(Messages.HAND_INIT.length()));

                } else if (content.startsWith(Messages.ADD_CARD)) {
                    // Aggiunge una carta alla mano
                    CardType type = CardType.valueOf(content.substring(Messages.ADD_CARD.length()));
                    hand.addCard(new Card(type, type.name(), ""));
                    System.out.println("Carta aggiunta: " + type);

                } else if (content.startsWith(Messages.REMOVE_CARD)) {
                    // Rimuove una carta dalla mano
                    CardType type = CardType.valueOf(content.substring(Messages.REMOVE_CARD.length()));
                    Card toRemove = hand.getCardOfType(type);
                    if (toRemove != null) hand.removeCard(toRemove);
                    System.out.println("Carta rimossa: " + type);

                } else if (content.equals(Messages.GET_HAND)) {
                    // Il PlayerAgent chiede la mano da mostrare all'utente
                    ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
                    reply.addReceiver(playerAgentAID);
                    reply.setContent(Messages.HAND_INIT + serializeHand());
                    send(reply);

                } else if (content.equals(Messages.HAS_DEFUSE_ASK)) {
                    // Il KittenDefenseAgent chiede se c'è un Defuse
                    boolean hasDefuse = hand.hasCardOfType(CardType.DEFUSE);
                    ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
                    reply.addReceiver(msg.getSender());
                    reply.setContent(hasDefuse ? Messages.HAS_DEFUSE_YES : Messages.HAS_DEFUSE_NO);
                    send(reply);

                } else if (content.equals(Messages.USE_DEFUSE)) {
                    // Il KittenDefenseAgent chiede di consumare il Defuse
                    Card defuse = hand.getCardOfType(CardType.DEFUSE);
                    if (defuse != null) {
                        hand.removeCard(defuse);
                        System.out.println("Defuse rimosso dalla mano.");
                    }
                }

            } else {
                block();
            }
        }

        private void initHand(String serialized) {
            if (serialized == null || serialized.isEmpty()) return;
            String[] types = serialized.split(",");
            for (String t : types) {
                if (!t.isEmpty()) {
                    CardType type = CardType.valueOf(t.trim());
                    hand.addCard(new Card(type, type.name(), ""));
                }
            }
            System.out.println("Mano inizializzata: " + serializeHand());
        }

        private String serializeHand() {
            StringBuilder sb = new StringBuilder();
            for (Card c : hand.getCards()) {
                sb.append(c.getType().name()).append(",");
            }
            return sb.toString();
        }
    }
}
