package exploding_kittens.remote;

import exploding_kittens.game.view.GameView;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlayerAgent extends Agent {

    private AID handManagerAID;
    private AID kittenDefenseAID;
    private String nickname;
    private GameView view;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        nickname = (args != null) ? args[0].toString() : getLocalName();
        view = new GameView();
        view.showWelcome(nickname);
        view.showWaitingForPlayers();
        startSubAgents();
        System.out.println("PlayerAgent " + nickname + " avviato.");
        addBehaviour(new RegisterToGameMasterBehaviour());
    }


    private void startSubAgents() {
        AgentContainer container = getContainerController();
        try {
            String handManagerName   = getLocalName() + "_HandManager";
            String kittenDefenseName = getLocalName() + "_KittenDefense";

            handManagerAID   = new AID(handManagerName,   AID.ISLOCALNAME);
            kittenDefenseAID = new AID(kittenDefenseName, AID.ISLOCALNAME);

            AgentController hm = container.createNewAgent(
                    handManagerName,
                    "exploding_kittens.remote.HandManagerAgent",
                    new Object[]{ getAID() }
            );
            AgentController kd = container.createNewAgent(
                    kittenDefenseName,
                    "exploding_kittens.remote.KittenDefenseAgent",
                    new Object[]{ getAID(), handManagerAID }
            );

            hm.start();
            kd.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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

    private class ListenFromGameMasterBehaviour extends CyclicBehaviour {
        private boolean waitingForInput = false;
        private boolean queryingForMaster  = false;
        private boolean handReady         = false;
        private boolean yourTurnPending   = false;

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.not(
                    MessageTemplate.MatchSender(myAgent.getAID())
            );
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String content     = msg.getContent();
                String senderLocal = msg.getSender().getLocalName();

                if (senderLocal.startsWith("GameMaster")) {
                    dispatchFromGameMaster(content);
                } else if (msg.getSender().getLocalName().equals(handManagerAID.getLocalName())) {
                    dispatchFromHandManagerAgent(content);
                } else {
                    dispatchFromKittenDefenseAgent(content);
                }
            } else {
                block();
            }
        }

        private void dispatchFromGameMaster(String content) {

            if (content.startsWith(Messages.HAND_INIT)) {
                sendMsgToSubAgent(handManagerAID, ACLMessage.INFORM, content);
                if (queryingForMaster) {
                    queryingForMaster = false;
                    String serialized = content.substring(Messages.HAND_INIT.length());
                    ACLMessage response = new ACLMessage(ACLMessage.INFORM);
                    response.addReceiver(new AID("GameMaster", AID.ISLOCALNAME));
                    response.setContent(Messages.HAND_RESPONSE + serialized);
                    send(response);

                } else {
                    String hand = content.substring(Messages.HAND_INIT.length());
                    view.showHand(parseHand(hand));
                    waitingForInput = true;

                    new Thread(() -> {
                        String input = view.askAction();
                        ACLMessage toGM = new ACLMessage(ACLMessage.REQUEST);
                        toGM.addReceiver(new AID("GameMaster", AID.ISLOCALNAME));
                        toGM.setContent(input);
                        myAgent.send(toGM);
                        waitingForInput = false;
                    }).start();
                }

            } else if (content.equals(Messages.REQUEST_HAND)) {
                queryingForMaster = true;
                sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);

            } else if (content.equals(Messages.YOUR_TURN)) {
                view.showYourTurn();
                queryingForMaster = false;
                waitingForInput = false;
                if (handReady) {
                    sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);
                } else {
                    yourTurnPending = true; // aspetta che HandManager sia pronto
                }

            } else if (content.startsWith(Messages.TURN_OF)) {
                view.showOtherPlayerTurn(content.substring(Messages.TURN_OF.length()));

            } else if (content.equals(Messages.DREW_KITTEN)) {
                view.showExplosion();
                sendMsgToSubAgent(kittenDefenseAID, ACLMessage.INFORM, Messages.KITTEN_DRAWN);

            } else if (content.startsWith(Messages.DREW)) {
                String cardType = content.substring(Messages.DREW.length());
                view.showCardDrawn(cardType);

            } else if (content.startsWith(Messages.ADD_CARD)) {
                sendMsgToSubAgent(handManagerAID, ACLMessage.INFORM, content);

            } else if (content.startsWith(Messages.REMOVE_CARD)) {
                sendMsgToSubAgent(handManagerAID, ACLMessage.INFORM, content);

            } else if (content.equals(Messages.REFRESH_HAND)) {
                queryingForMaster = false;
                waitingForInput = false;
                sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);

            } else if (content.startsWith(Messages.SEE_THE_FUTURE)) {
                String[] cards = content.substring(Messages.SEE_THE_FUTURE.length()).split(",");
                view.showSeeTheFuture(Arrays.asList(cards));

            } else if (content.startsWith(Messages.YOU_STOLE)) {
                // Sei tu ad aver rubato
                String stolenType = content.substring(Messages.YOU_STOLE.length());
                view.showCardPlayed(nickname, "CAT_CARD");
                view.showCardDrawn(stolenType);

            } else if (content.startsWith(Messages.STOLEN_FROM_YOU)) {
                // Ti hanno rubato una carta
                String stolenType = content.substring(Messages.STOLEN_FROM_YOU.length());
                view.showStolenCard(stolenType);

            } else if (content.equals(Messages.DEFUSED)) {
                view.showDefuseUsed();

            } else if (content.equals(Messages.SKIP_OK)) {
                view.showCardPlayed(nickname, "SKIP");

            } else if (content.equals(Messages.ATTACK_OK)) {
                view.showCardPlayed(nickname, "ATTACK");

            } else if (content.equals(Messages.SHUFFLE_OK)) {
                view.showShuffled();

            } else if (content.equals(Messages.CARD_NOT_IN_HAND)) {
                view.showCardNotInHand();
                waitingForInput = false;
                sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);

            } else if (content.equals(Messages.NOT_YOUR_TURN)) {
                view.showNotYourTurn();

            } else if (content.equals(Messages.MISSING_TARGET)
                    || content.equals(Messages.INVALID_TARGET)) {
                view.showError("Target non valido.");
                waitingForInput = false;
                sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);

            } else if (content.startsWith(Messages.WINNER)) {
                view.showGameOver(content.substring(Messages.WINNER.length()));

            } else {
                // Messaggi broadcast generici (es. eliminazioni, notifiche)
                view.showError(content);
            }
        }


        private void dispatchFromHandManagerAgent(String content) {

            if (content.equals(Messages.HAND_READY)) {
                handReady = true;
                if (yourTurnPending) {
                    yourTurnPending = false;
                    sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);
                }
                return;
            }

            if (content.startsWith(Messages.HAND_INIT)) {
                if (queryingForMaster) {
                    queryingForMaster = false;
                    String serialized = content.substring(Messages.HAND_INIT.length());
                    ACLMessage response = new ACLMessage(ACLMessage.INFORM);
                    response.addReceiver(new AID("GameMaster", AID.ISLOCALNAME));
                    response.setContent(Messages.HAND_RESPONSE + serialized);
                    send(response);
                } else {
                    String hand = content.substring(Messages.HAND_INIT.length());
                    view.showHand(parseHand(hand));
                    waitingForInput = true;

                    new Thread(() -> {
                        String input = view.askAction();
                        ACLMessage toGM = new ACLMessage(ACLMessage.REQUEST);
                        toGM.addReceiver(new AID("GameMaster", AID.ISLOCALNAME));
                        toGM.setContent(input);
                        myAgent.send(toGM);
                        waitingForInput = false;
                    }).start();
                }
            }
        }

        private void dispatchFromKittenDefenseAgent(String content) {
            System.out.println("[DEBUG PlayerAgent] Ricevuto comando da KittenDefense: " + content);
            if (content.startsWith(Messages.PLAY)
                    || content.equals(Messages.DRAW)
                    || content.startsWith(Messages.DEFUSE_PLAY)
                    || content.startsWith(Messages.CAT_CARD_PLAY)) {
                System.out.println("[DEBUG PlayerAgent] Il comando è valido. Provo a inviarlo al GameMaster...");

                ACLMessage toGM = new ACLMessage(ACLMessage.REQUEST);
                toGM.addReceiver(new AID("GameMaster", AID.ISLOCALNAME));
                toGM.setContent(content);
                send(toGM);
                System.out.println("[DEBUG PlayerAgent] Messaggio inviato al GameMaster!");
            } else if (content.equals(Messages.SHOW_DEFUSE_USED)) {
                view.showDefuseUsed();

            } else if (content.equals(Messages.SHOW_ELIMINATED)) {
                view.showYouAreEliminated();

            } else if (content.equals(Messages.ASK_DEFUSE_POSITION)) {
                // Usiamo un Thread per non bloccare l'agente mentre la finestra è aperta
                new Thread(() -> {
                    int position = view.askDefusePosition(0);

                    System.out.println("[DEBUG LOCAL] Hai scelto la posizione: " + position);

                    ACLMessage defuseMsg = new ACLMessage(ACLMessage.REQUEST);
                    defuseMsg.addReceiver(new AID("GameMaster", AID.ISLOCALNAME));
                    defuseMsg.setContent(Messages.DEFUSE_PLAY  + position);
                    myAgent.send(defuseMsg);

                    System.out.println("[DEBUG LOCAL] Messaggio DEFUSE inviato al GameMaster!");
                }).start();

            } else if (content.equals(Messages.PLAYER_ELIMINATED)) {
                ACLMessage toGM = new ACLMessage(ACLMessage.INFORM);
                toGM.addReceiver(new AID("GameMaster", AID.ISLOCALNAME));
                toGM.setContent(Messages.PLAYER_ELIMINATED);
                send(toGM);

            } else if (content.equals(Messages.REFRESH_HAND_AFTER_DEFUSE)) {
                queryingForMaster = false;
                waitingForInput = false;
                sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);
            }
        }
    }


    private void sendMsgToSubAgent(AID target, int performative, String content) {
        ACLMessage msg = new ACLMessage(performative);
        msg.addReceiver(target);
        msg.setContent(content);
        send(msg);
    }

    private List<String> parseHand(String serialized) {
        List<String> cards = new ArrayList<>();
        if (serialized == null || serialized.isEmpty()) return cards;
        for (String s : serialized.split(",")) {
            if (!s.isEmpty()) cards.add(s.trim());
        }
        return cards;
    }
}