package exploding_kittens.remote;

import exploding_kittens.game.view.GameView;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
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
    private AID gameMasterAID;
    private String nickname;
    private GameView view;
    private int requestedPlayers;
    private boolean gameStarted = false;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        nickname = (args != null) ? args[0].toString() : getLocalName();

        if (args != null && args.length >= 2) {
            requestedPlayers = (int) args[1];
        } else {
            requestedPlayers = 2; // Default
        }

        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("player"); // Il Backup cercherà questo tipo
            sd.setName(nickname);
            dfd.addServices(sd);
            DFService.register(this, dfd);
        } catch (Exception e) { e.printStackTrace(); }
        view = new GameView();
        view.showWelcome(nickname);
        view.showWaitingForPlayers();
        startSubAgents();
        System.out.println("PlayerAgent " + nickname + " avviato.");
        addBehaviour(new RegisterToGameMasterBehaviour());
        addBehaviour(new StartHeartBeatBehaviour(this));
    }

    private AID findGameMaster() {
        try {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("game-master");
            template.addServices(sd);

            DFAgentDescription[] result = DFService.search(this, template);

            if (result.length > 0) {
                return result[0].getName();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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
            while (gameMasterAID == null) {
                gameMasterAID = findGameMaster();
                if (gameMasterAID == null) {
                    System.out.println("GameMaster non trovato, ritento...");
                    try { Thread.sleep(1000); } catch (Exception e) {}
                }
            }

            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(gameMasterAID);
            msg.setContent(Messages.JOIN + ":" + requestedPlayers);
            send(msg);
            System.out.println(nickname + " inviata richiesta di join per partita da " + requestedPlayers);
            addBehaviour(new WaitForConfirmBehaviour());
        }
    }

    //Behaviour per l'invio degli heartbeat (necessario per gestire failure dei client)
    private class StartHeartBeatBehaviour extends TickerBehaviour {
        public StartHeartBeatBehaviour(Agent a) {
            super(a, 3000);
        }

        @Override
        protected void onTick() {
                if (gameMasterAID != null) {
                    ACLMessage hb = new ACLMessage(ACLMessage.INFORM);
                    hb.addReceiver(gameMasterAID);
                    hb.setContent(Messages.HEARTBEAT_CLIENT);
                    send(hb);
                }
        }
    }

    private class WaitForConfirmBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.or(
                    MessageTemplate.MatchPerformative(ACLMessage.CONFIRM),
                    MessageTemplate.MatchPerformative(ACLMessage.REFUSE)
            );
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                if (msg.getPerformative() == ACLMessage.CONFIRM) {
                    System.out.println(nickname + " registrato alla partita!");
                    myAgent.removeBehaviour(this);
                    addBehaviour(new ListenFromGameMasterBehaviour());
                    addBehaviour(new BackupListenerBehaviour());
                } else if (msg.getPerformative() == ACLMessage.REFUSE) {
                    // Gestione lobby piena
                    view.showError("Impossibile entrare: la lobby è piena o la partita è già iniziata.");
                    System.out.println("Accesso negato dal GameMaster: Lobby Piena.");
                    // Opzionale: chiudere l'agente o tornare al setup
                    // myAgent.doDelete();
                }
            } else {
                block();
            }
        }
    }

    /*Gestione caso in cui MasterAgent primario fallisce*/
    private class BackupListenerBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchContent(Messages.NEW_MASTER);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                System.out.println("[DEBUG-PLAYER] " + nickname + " ha ricevuto NEW_MASTER da " + msg.getSender().getLocalName());
                System.out.println("[DEBUG-PLAYER] gameStarted = " + gameStarted + " (Se false, invio JOIN)");
                gameMasterAID = msg.getSender();
                System.out.println("Switch effettuato: nuovo Master è " + gameMasterAID.getLocalName());
                view.showError("Il Master primario è caduto. Backup attivato!"); //DEBUG
                sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);
                // --- LOGICA DI RE-JOIN ---
                if (!gameStarted) {
                    System.out.println(nickname + ": Ri-invio richiesta di join al nuovo Master...");
                    ACLMessage rejoin = new ACLMessage(ACLMessage.REQUEST);
                    rejoin.addReceiver(gameMasterAID);
                    rejoin.setContent(Messages.JOIN + ":" + requestedPlayers);
                    send(rejoin);
                } else {
                    // Se la partita era già iniziata, chiediamo solo un refresh della mano
                    sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);
                }
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
                AID sender = msg.getSender();

                //!!!!!!!!! NON SONO CONVINTA DI QUESTA SOLUZIONE !!!!!!!!!
                //TODO: valutare se è meglio gestire il caso del Backup che diventa Master in modo diverso, magari con un comportamento dedicato
                if(content.equals(Messages.NEW_MASTER)){
                    gameMasterAID = sender;
                }
                if (sender.equals(gameMasterAID)) {
                    dispatchFromGameMaster(content);
                } else if (sender.getLocalName().equals(handManagerAID.getLocalName())) {
                    dispatchFromHandManagerAgent(content);
                } else if (sender.getLocalName().equals(kittenDefenseAID.getLocalName())) {
                    dispatchFromKittenDefenseAgent(content);
                } else {
                    System.out.println("Messaggio ricevuto da mittente non riconosciuto: " + sender.getLocalName());
                }
            } else {
                block();
            }
        }

        private void dispatchFromGameMaster(String content) {

            if (content.startsWith(Messages.HAND_INIT)) {
                gameStarted = true;
                sendMsgToSubAgent(handManagerAID, ACLMessage.INFORM, content);
                if (queryingForMaster) {
                    queryingForMaster = false;
                    String serialized = content.substring(Messages.HAND_INIT.length());
                    ACLMessage response = new ACLMessage(ACLMessage.INFORM);
                    response.addReceiver(gameMasterAID);
                    response.setContent(Messages.HAND_RESPONSE + serialized);
                    send(response);
                    //view.updatePlayersList(List.of(Messages.YOUR_TURN));

                } else {
                    String hand = content.substring(Messages.HAND_INIT.length());
                    view.showHand(parseHand(hand));
                    waitingForInput = true;

                    new Thread(() -> {
                        String input = view.askAction();
                        ACLMessage toGM = new ACLMessage(ACLMessage.REQUEST);
                        toGM.addReceiver(gameMasterAID);
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

            }  else if (content.startsWith(Messages.TURN_OF)) {
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

            }else if (content.startsWith("PLAYERS_LIST:")) {
                String[] names = content.substring("PLAYERS_LIST:".length()).split(",");
                List<String> allPlayers = Arrays.stream(names)
                        .filter(n -> !n.isBlank())
                        .toList();
                view.updatePlayersList(allPlayers);
            } else if (content.startsWith("PLAYER_DISCONNECTED:")) {
                String nick = content.substring("PLAYER_DISCONNECTED:".length());
                view.showPlayerDisconnected(nick);
            }
            else {
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
                    response.addReceiver(gameMasterAID);
                    response.setContent(Messages.HAND_RESPONSE + serialized);
                    send(response);
                } else {
                    String hand = content.substring(Messages.HAND_INIT.length());
                    view.showHand(parseHand(hand));
                    waitingForInput = true;

                    new Thread(() -> {
                        String input = view.askAction();
                        ACLMessage toGM = new ACLMessage(ACLMessage.REQUEST);
                        toGM.addReceiver(gameMasterAID);
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
                toGM.addReceiver(gameMasterAID);
                toGM.setContent(content);
                send(toGM);
                System.out.println("[DEBUG PlayerAgent] Messaggio inviato al GameMaster!");
            } else if (content.equals(Messages.SHOW_DEFUSE_USED)) {
                view.showDefuseUsed();

            } else if (content.equals(Messages.SHOW_ELIMINATED)) {
                view.showYouAreEliminated();
                view.removePlayerFromList(nickname);

            } else if (content.equals(Messages.ASK_DEFUSE_POSITION)) {
                // Usiamo un Thread per non bloccare l'agente mentre la finestra è aperta
                new Thread(() -> {
                    int position = view.askDefusePosition(0);

                    System.out.println("[DEBUG LOCAL] Hai scelto la posizione: " + position);

                    ACLMessage defuseMsg = new ACLMessage(ACLMessage.REQUEST);
                    defuseMsg.addReceiver(gameMasterAID);
                    defuseMsg.setContent(Messages.DEFUSE_PLAY  + position);
                    myAgent.send(defuseMsg);

                    System.out.println("[DEBUG LOCAL] Messaggio DEFUSE inviato al GameMaster!");
                }).start();

            } else if (content.equals(Messages.PLAYER_ELIMINATED)) {
                ACLMessage toGM = new ACLMessage(ACLMessage.INFORM);
                toGM.addReceiver(gameMasterAID);
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