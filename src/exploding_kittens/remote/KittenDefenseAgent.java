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

    //Aspettta risposta da HandManager
    private class WaitForDefuseCheckBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchSender(handManagerAID)
            );
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String content = msg.getContent();
                myAgent.removeBehaviour(this);

                if (content.equals(Messages.HAS_DEFUSE_YES)) {
                    addBehaviour(new UseDefuseBehaviour());
                } else if (content.equals(Messages.HAS_DEFUSE_NO)) {
                    // Nessun Defuse: il giocatore è eliminato
                    System.out.println("KittenDefenseAgent: nessun Defuse disponibile.");
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

    //usa Defuse
    private class UseDefuseBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            // Chiede all'HandManager di rimuovere il Defuse dalla mano
            ACLMessage useDefuse = new ACLMessage(ACLMessage.INFORM);
            useDefuse.addReceiver(handManagerAID);
            useDefuse.setContent(Messages.USE_DEFUSE);
            send(useDefuse);

            // Chiede all'utente dove reinserire il Kitten nel mazzo
            System.out.println("Hai un Defuse! In che posizione vuoi reinserire il Kitten? (0 = cima del mazzo)");
            Scanner scanner = new Scanner(System.in);
            int position = scanner.nextInt();

            // Comunica la mossa al PlayerAgent che la inoltrerà al GameMaster
            ACLMessage defuse = new ACLMessage(ACLMessage.INFORM);
            defuse.addReceiver(playerAgentAID);
            defuse.setContent(Messages.DEFUSE_PLAY + position);
            send(defuse);
        }
    }
}