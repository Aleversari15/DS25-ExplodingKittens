package exploding_kittens.remote;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

import java.util.Scanner;

public class PlayerAgent extends Agent {

    private AID handManagerAID;
    private AID kittenDefenseAID;
    private String nickname;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        nickname = (args != null) ? args[0].toString() : getLocalName();

        startSubAgents();

        System.out.println("PlayerAgent " + nickname + " avviato.");
        addBehaviour(new RegisterToGameMasterBehaviour());
    }

    //avvia i sottoagenti
    private void startSubAgents() {
        AgentContainer container = getContainerController();
        try {
            String handManagerName  = getLocalName() + "_HandManager";
            String kittenDefenseName = getLocalName() + "_KittenDefense";

            handManagerAID   = new AID(handManagerName,   AID.ISLOCALNAME);
            kittenDefenseAID = new AID(kittenDefenseName, AID.ISLOCALNAME);

            AgentController hm = container.createNewAgent(
                    handManagerName,
                    "exploding_kittens.remote.player.HandManagerAgent",
                    new Object[]{ getAID() }
            );
            AgentController kd = container.createNewAgent(
                    kittenDefenseName,
                    "exploding_kittens.remote.player.KittenDefenseAgent",
                    new Object[]{ getAID(), handManagerAID }
            );

            hm.start();
            kd.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Registrazione
    private class RegisterToGameMasterBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(new AID("GameMaster", AID.ISLOCALNAME));
            msg.setContent(Messages.JOIN);
            send(msg);

            addBehaviour(new WaitForConfirmBehaviour());
        }
    }

    //Aspetta conferma Master
    private class WaitForConfirmBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CONFIRM);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null && msg.getContent().equals(Messages.JOINED)) {
                System.out.println(nickname + " registrato alla partita!");
                myAgent.removeBehaviour(this);
                addBehaviour(new ListenFromGameMasterBehaviour());
            } else {
                block();
            }
        }
    }

    //smista msg
    private class ListenFromGameMasterBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();

            if (msg != null) {
                String content = msg.getContent();
                String senderLocal = msg.getSender().getLocalName();

                if (senderLocal.equals("GameMaster")) {
                    dispatchFromGameMaster(content);
                } else {
                    dispatchFromSubAgent(content);
                }
            } else {
                block();
            }
        }

        // Smista i messaggi DAL GameMaster
        private void dispatchFromGameMaster(String content) {

            if (content.startsWith(Messages.HAND_INIT)) {
                sendMsgToSubAgent(handManagerAID, ACLMessage.INFORM, content);

            } else if (content.equals(Messages.YOUR_TURN)) {
                System.out.println("[" + nickname + "] È il tuo turno!");
                addBehaviour(new AskHandThenPlayBehaviour());

            } else if (content.startsWith(Messages.TURN_OF)) {
                System.out.println("[" + nickname + "] Turno di: " + content.substring(Messages.TURN_OF.length()));

            } else if (content.equals(Messages.DREW_KITTEN)) {
                System.out.println("[" + nickname + "] Hai pescato un Exploding Kitten!");
                sendMsgToSubAgent(kittenDefenseAID, ACLMessage.INFORM, Messages.KITTEN_DRAWN);

            } else if (content.startsWith(Messages.DREW)) {
                String cardType = content.substring(Messages.DREW.length());
                sendMsgToSubAgent(handManagerAID, ACLMessage.INFORM, Messages.ADD_CARD + cardType);
                System.out.println("[" + nickname + "] Hai pescato: " + cardType + ". Turno terminato.");

            } else if (content.startsWith(Messages.SEE_THE_FUTURE)) {
                System.out.println("[" + nickname + "] Prossime 3 carte: " + content.substring(Messages.SEE_THE_FUTURE.length()));

            } else if (content.startsWith(Messages.STOLEN)) {
                String cardType = content.substring(Messages.STOLEN.length());
                System.out.println("[" + nickname + "] Ti è stata rubata: " + cardType);
                sendMsgToSubAgent(handManagerAID, ACLMessage.INFORM, Messages.REMOVE_CARD + cardType);

            } else if (content.equals(Messages.DEFUSED)) {
                System.out.println("[" + nickname + "] Kitten defusato con successo!");

            } else if (content.equals(Messages.SKIP_OK)) {
                System.out.println("[" + nickname + "] Skip eseguito.");

            } else if (content.equals(Messages.ATTACK_OK)) {
                System.out.println("[" + nickname + "] Attack eseguito.");

            } else if (content.equals(Messages.SHUFFLE_OK)) {
                System.out.println("[" + nickname + "] Mazzo mischiato.");

            } else if (content.equals(Messages.CARD_NOT_IN_HAND)) {
                System.out.println("[" + nickname + "] Carta non presente in mano!");

            } else if (content.equals(Messages.NOT_YOUR_TURN)) {
                System.out.println("[" + nickname + "] Non è il tuo turno!");

            } else if (content.startsWith(Messages.WINNER)) {
                System.out.println("[" + nickname + "] Vincitore: " + content.substring(Messages.WINNER.length()));
            }
        }

        // Smista i messaggi DAI sottoagenti verso il GameMaster
        private void dispatchFromSubAgent(String content) {
            if (content.startsWith(Messages.PLAY)
                    || content.equals(Messages.DRAW)
                    || content.startsWith(Messages.DEFUSE_PLAY)
                    || content.startsWith(Messages.CAT_CARD_PLAY)) {

                ACLMessage toGM = new ACLMessage(ACLMessage.REQUEST);
                toGM.addReceiver(new AID("GameMaster", AID.ISLOCALNAME));
                toGM.setContent(content);
                send(toGM);

            } else if (content.equals(Messages.PLAYER_ELIMINATED)) {
                // Notifica il GameMaster che questo giocatore è eliminato
                ACLMessage toGM = new ACLMessage(ACLMessage.INFORM);
                toGM.addReceiver(new AID("GameMaster", AID.ISLOCALNAME));
                toGM.setContent(Messages.PLAYER_ELIMINATED);
                send(toGM);
                System.out.println("[" + nickname + "] Sei stato eliminato!");
            }
        }
    }

    //Chiede la mano da HandManager
    private class AskHandThenPlayBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);
            addBehaviour(new WaitForUserInputBehaviour());
        }
    }

    //Recupera la mano da HandManager
    private class WaitForUserInputBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchSender(handManagerAID)
            );
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null && msg.getContent().startsWith(Messages.HAND_INIT)) {
                String hand = msg.getContent().substring(Messages.HAND_INIT.length());
                System.out.println("[" + nickname + "] La tua mano: " + hand);
                System.out.println("Digita: DRAW | PLAY:<CARD_TYPE> | CAT_CARD:<targetAgent>");

                Scanner scanner = new Scanner(System.in);
                String input = scanner.nextLine().trim().toUpperCase();

                ACLMessage toGM = new ACLMessage(ACLMessage.REQUEST);
                toGM.addReceiver(new AID("GameMaster", AID.ISLOCALNAME));
                toGM.setContent(input);
                send(toGM);

                myAgent.removeBehaviour(this);
            } else {
                block();
            }
        }
    }


    private void sendMsgToSubAgent(AID target, int performative, String content) {
        ACLMessage msg = new ACLMessage(performative);
        msg.addReceiver(target);
        msg.setContent(content);
        send(msg);
    }
}
