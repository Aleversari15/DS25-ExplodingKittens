package exploding_kittens.remote;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.Scanner;

public class KittenDefenseAgent extends Agent {

    private AID playerAgentAID;
    private AID handManagerAID;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        playerAgentAID = (AID) args[0];
        handManagerAID = (AID) args[1];

        System.out.println("KittenDefenseAgent avviato.");
        addBehaviour(new ListenForKittenBehaviour());
    }

    //Aspetta segnale da PlayerAgent
    private class ListenForKittenBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchSender(playerAgentAID)
            );
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null && msg.getContent().equals(Messages.KITTEN_DRAWN)) {
                System.out.println("KittenDefenseAgent: Kitten pescato, controllo Defuse...");

                // Chiede all'HandManager se c'è un Defuse disponibile
                ACLMessage ask = new ACLMessage(ACLMessage.INFORM);
                ask.addReceiver(handManagerAID);
                ask.setContent(Messages.HAS_DEFUSE_ASK);
                send(ask);

                addBehaviour(new WaitForDefuseCheckBehaviour());
            } else {
                block();
            }
        }
    }

    // Aspetta risposta da HandManager
    private class WaitForDefuseCheckBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchSender(handManagerAID)
            );
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                myAgent.removeBehaviour(this);
                if (msg.getContent().equals(Messages.HAS_DEFUSE_YES)) {
                    addBehaviour(new UseDefuseBehaviour());
                } else if (msg.getContent().equals(Messages.HAS_DEFUSE_NO)) {
                    // Chiede al PlayerAgent di mostrare il messaggio di eliminazione
                    ACLMessage notify = new ACLMessage(ACLMessage.INFORM);
                    notify.addReceiver(playerAgentAID);
                    notify.setContent(Messages.SHOW_ELIMINATED);
                    send(notify);

                    // Comunica l'eliminazione al PlayerAgent
                    ACLMessage eliminated = new ACLMessage(ACLMessage.INFORM);
                    eliminated.addReceiver(playerAgentAID);
                    eliminated.setContent(Messages.PLAYER_ELIMINATED);
                    send(eliminated);
                }
            } else {
                block();
            }
        }
    }

    // Usa il Defuse e chiede la posizione al PlayerAgent
    private class UseDefuseBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            // Rimuove il Defuse dalla mano tramite HandManager
            ACLMessage useDefuse = new ACLMessage(ACLMessage.INFORM);
            useDefuse.addReceiver(handManagerAID);
            useDefuse.setContent(Messages.USE_DEFUSE);
            send(useDefuse);

            // Chiede al PlayerAgent di raccogliere la posizione dall'utente
            ACLMessage ask = new ACLMessage(ACLMessage.INFORM);
            ask.addReceiver(playerAgentAID);
            ask.setContent(Messages.ASK_DEFUSE_POSITION);
            send(ask);

            // Aspetta la risposta con la posizione
            addBehaviour(new WaitForDefusePositionBehaviour());
        }
    }

    // Aspetta la posizione dal PlayerAgent e la inoltra al GameMaster
    private class WaitForDefusePositionBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchSender(playerAgentAID)
            );
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null && msg.getContent().startsWith(Messages.DEFUSE_PLAY)) {
                // Inoltra la mossa al PlayerAgent che la manderà al GameMaster
                ACLMessage defuse = new ACLMessage(ACLMessage.INFORM);
                defuse.addReceiver(playerAgentAID);
                defuse.setContent(msg.getContent());
                send(defuse);

                myAgent.removeBehaviour(this);
            } else {
                block();
            }
        }
    }
}