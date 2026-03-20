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

//TODO: rimuovere tutti i log al termine dei test
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
        private boolean waitingForInput = false;
        @Override
        public void action() {
            // Esclude i messaggi che arrivano dall'agente stesso
            MessageTemplate mt = MessageTemplate.not(
                    MessageTemplate.MatchSender(myAgent.getAID())
            );
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String content = msg.getContent();
                String senderLocal = msg.getSender().getLocalName();
                System.out.println("DEBUG ricevuto da: '" + senderLocal + "' | content: '" + content + "' | waitingForInput: " + waitingForInput);

                if (senderLocal.startsWith("GameMaster")) {
                    dispatchFromGameMaster(content);
                } else if (msg.getSender().getLocalName().equals(handManagerAID.getLocalName())) {
                    // Risposta HAND_INIT dall'HandManager
                    if (content.startsWith(Messages.HAND_INIT) && !waitingForInput) {
                        waitingForInput = true;
                        String hand = content.substring(Messages.HAND_INIT.length());
                        view.showHand(parseHand(hand));

                        new Thread(() -> {
                            String input = view.askAction();
                            ACLMessage toGM = new ACLMessage(ACLMessage.REQUEST);
                            toGM.addReceiver(new AID("GameMaster", AID.ISLOCALNAME));
                            toGM.setContent(input);
                            myAgent.send(toGM);
                            waitingForInput = false;
                        }).start();
                    }
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
                view.showYourTurn();
                waitingForInput = false;
                sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);

            } else if (content.startsWith(Messages.TURN_OF)) {
                System.out.println("[" + nickname + "] Turno di: " + content.substring(Messages.TURN_OF.length()));
                view.showOtherPlayerTurn(content.substring(Messages.TURN_OF.length()));

            } else if (content.equals(Messages.DREW_KITTEN)) {
                System.out.println("[" + nickname + "] Hai pescato un Exploding Kitten!");
                view.showExplosion();
                sendMsgToSubAgent(kittenDefenseAID, ACLMessage.INFORM, Messages.KITTEN_DRAWN);


            } else if (content.startsWith(Messages.DREW)) {
                String cardType = content.substring(Messages.DREW.length());
                view.showCardDrawn(cardType);
                sendMsgToSubAgent(handManagerAID, ACLMessage.INFORM, Messages.ADD_CARD + cardType);
                System.out.println("[" + nickname + "] Hai pescato: " + cardType + ". Turno terminato.");

            } else if (content.startsWith(Messages.SEE_THE_FUTURE)) {
                String[] cards = content.substring(Messages.SEE_THE_FUTURE.length()).split(",");
                view.showSeeTheFuture(Arrays.asList(cards));
                System.out.println("[" + nickname + "] Prossime 3 carte: " + content.substring(Messages.SEE_THE_FUTURE.length()));
                waitingForInput = false; // See the future non termina il turno
                sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);

            } else if (content.startsWith(Messages.STOLEN)) {
                String cardType = content.substring(Messages.STOLEN.length());
                System.out.println("[" + nickname + "] Ti è stata rubata: " + cardType);
                view.showStolenCard(cardType);
                sendMsgToSubAgent(handManagerAID, ACLMessage.INFORM, Messages.REMOVE_CARD + cardType);

            } else if (content.equals(Messages.DEFUSED)) {
                System.out.println("[" + nickname + "] Kitten defusato con successo!");
                view.showDefuseUsed();

            } else if (content.equals(Messages.SKIP_OK)) {
                System.out.println("[" + nickname + "] Skip eseguito.");
                view.showCardPlayed(nickname, "SKIP");

            } else if (content.equals(Messages.ATTACK_OK)) {
                System.out.println("[" + nickname + "] Attack eseguito.");
                view.showCardPlayed(nickname, "ATTACK");

            } else if (content.equals(Messages.SHUFFLE_OK)) {
                System.out.println("[" + nickname + "] Mazzo mischiato.");
                view.showShuffled();
                waitingForInput = false; // Shuffle non termina il turno
                sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);

            } else if (content.equals(Messages.CARD_NOT_IN_HAND)) {
                System.out.println("[" + nickname + "] Carta non presente in mano!");
                view.showCardNotInHand();

                waitingForInput = false; // Carta non valida, riprovare
                sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);

            } else if (content.equals(Messages.NOT_YOUR_TURN)) {
                System.out.println("[" + nickname + "] Non è il tuo turno!");
                view.showNotYourTurn();

            } else if (content.startsWith(Messages.WINNER)) {
                view.showGameOver(content.substring(Messages.WINNER.length()));
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

            } else if (content.equals(Messages.SHOW_DEFUSE_USED)) {
                view.showDefuseUsed();

            } else if (content.equals(Messages.SHOW_ELIMINATED)) {
                view.showYouAreEliminated();

            } else if (content.equals(Messages.ASK_DEFUSE_POSITION)) {
                // Raccoglie la posizione su thread separato e la rimanda al KittenDefenseAgent
                new Thread(() -> {
                    int position = view.askDefusePosition(0);
                    ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
                    reply.addReceiver(kittenDefenseAID);
                    reply.setContent(Messages.DEFUSE_PLAY + position);
                    myAgent.send(reply);
                }).start();

            } else if (content.equals(Messages.PLAYER_ELIMINATED)) {
                ACLMessage toGM = new ACLMessage(ACLMessage.INFORM);
                toGM.addReceiver(new AID("GameMaster", AID.ISLOCALNAME));
                toGM.setContent(Messages.PLAYER_ELIMINATED);
                send(toGM);
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
