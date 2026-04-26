package explodingkittens.remote;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;


/**
 * Agente responsabile della gestione della difesa quando un giocatore pesca un Exploding Kitten.
 * La sua responsabilità principale è verificare il possesso di un Defuse, utilizzarlo se presente, o gestire l'eliminazione
 * del giocatore se assente.
 */
public class KittenDefenseAgent extends Agent {
    private AID playerAgentAID;
    private AID handManagerAID;
    private String currentDeckSize;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        playerAgentAID = (AID) args[0];
        handManagerAID = (AID) args[1];
        addBehaviour(new ListenForKittenBehaviour());
    }

    /**
     * Comportamento che resta in ascolto del segnale KITTEN_DRAWN dal PlayerAgent.
     * Quando viene pescato un exploding kitten, avvia la procedura di verifica del Defuse interpellando l'HandManager.
     */
    private class ListenForKittenBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchSender(playerAgentAID)
            );
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null && msg.getContent().startsWith(Messages.KITTEN_DRAWN)) {
                String content = msg.getContent();
                if (content.contains(":")) {
                    currentDeckSize = content.split(":")[1];
                }
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

    /**
     * Comportamento che attende la risposta dall'HandManager riguardo la presenza di un Defuse.
     * Gestisce i due scenari possibili: uso del Defuse o eliminazione del giocatore.
     */
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
                    ACLMessage notify = new ACLMessage(ACLMessage.INFORM);
                    notify.addReceiver(playerAgentAID);
                    notify.setContent(Messages.SHOW_ELIMINATED);
                    send(notify);

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
    /**
     * Comportamento che esegue l'azione di utilizzare una carta Defuse.
     * Ordina all'HandManager di rimuovere la carta, aggiorna l'interfaccia del giocatore
     * e richiede la posizione dove reinserire l'Exploding Kitten nel mazzo.
     */
    private class UseDefuseBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            ACLMessage useDefuse = new ACLMessage(ACLMessage.INFORM);
            useDefuse.addReceiver(handManagerAID);
            useDefuse.setContent(Messages.USE_DEFUSE);
            send(useDefuse);

            ACLMessage refresh = new ACLMessage(ACLMessage.INFORM);
            refresh.addReceiver(playerAgentAID);
            refresh.setContent(Messages.REFRESH_HAND_AFTER_DEFUSE);
            send(refresh);

            ACLMessage ask = new ACLMessage(ACLMessage.INFORM);
            ask.addReceiver(playerAgentAID);
            ask.setContent(Messages.ASK_DEFUSE_POSITION + currentDeckSize);
            send(ask);

            addBehaviour(new WaitForExplodingPositionBehaviour());
        }
    }
    /**
     * Comportamento che attende la scelta della posizione del exploding kitten da parte del giocatore.
     * Una volta ricevuta la posizione, invia il comando finale per rimetterlo nel mazzo.
     */
    private class WaitForExplodingPositionBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchSender(playerAgentAID)
            );
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                if (msg.getContent().startsWith(Messages.DEFUSE_PLAY)) {
                    ACLMessage defuse = new ACLMessage(ACLMessage.REQUEST);
                    defuse.addReceiver(playerAgentAID);
                    defuse.setContent(msg.getContent());
                    send(defuse);
                    myAgent.removeBehaviour(this);
                }
            } else {
                block();
            }
        }
    }
}