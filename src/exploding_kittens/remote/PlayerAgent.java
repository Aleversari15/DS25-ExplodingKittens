package exploding_kittens.remote;

import exploding_kittens.game.model.CardType;
import exploding_kittens.game.view.GameView;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
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
/**
 * Rappresenta l'agente principale del giocatore nel sistema multi-agente.
 * Gestisce l'interfaccia utente (GameView), la registrazione al GameMaster e il coordinamento
 * con i sotto-agenti specializzati per la gestione della mano e la difesa dagli exploding kittens.
 */
public class PlayerAgent extends Agent {

    private AID handManagerAID;
    private AID kittenDefenseAID;
    private AID gameMasterAID;
    private String nickname;
    private GameView view;
    private int requestedPlayers;
    private boolean gameStarted = false;

    /**
     * Inizializza l'agente, registra i servizi nel DF, avvia i sotto-agenti e
     * inizializza l'interfaccia grafica.
     */
    @Override
    protected void setup() {
        Object[] args = getArguments();
        nickname = (args != null) ? args[0].toString() : getLocalName();
        requestedPlayers = (args != null && args.length >= 2) ? (int) args[1] : 2;

        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("player");
            sd.setName(nickname);
            dfd.addServices(sd);
            DFService.register(this, dfd);
        } catch (Exception e) {
            e.printStackTrace();
        }

        view = new GameView();
        view.showWelcome(nickname);
        view.showWaitingForPlayers();

        startSubAgents();

        System.out.println("PlayerAgent " + nickname + " avviato.");
        addBehaviour(new RegisterToGameMasterBehaviour());
        addBehaviour(new jade.core.behaviours.TickerBehaviour(this, 5000) {
            @Override
            protected void onTick() {
                if (gameMasterAID != null) {
                    ACLMessage hb = new ACLMessage(ACLMessage.INFORM);
                    hb.addReceiver(gameMasterAID);
                    hb.setContent(Messages.HEARTBEAT_CLIENT);
                    myAgent.send(hb);
                }
            }
        });
    }
    /**
     * Crea e avvia dinamicamente i sotto-agenti HandManager e KittenDefense
     * all'interno dello stesso container del PlayerAgent.
     */
    private void startSubAgents() {
        AgentContainer container = getContainerController();
        try {
            String hmName = getLocalName() + "_HandManager";
            String kdName = getLocalName() + "_KittenDefense";
            handManagerAID = new AID(hmName, AID.ISLOCALNAME);
            kittenDefenseAID = new AID(kdName, AID.ISLOCALNAME);

            container.createNewAgent(hmName, "exploding_kittens.remote.HandManagerAgent", new Object[]{getAID()}).start();
            container.createNewAgent(kdName, "exploding_kittens.remote.KittenDefenseAgent", new Object[]{getAID(), handManagerAID}).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- COMPORTAMENTI ---
    /**
     * Comportamento che cerca il GameMaster nel Directory Facilitator (DF)
     * e avvia la procedura di registrazione (JOIN).
     */
    private class RegisterToGameMasterBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            while (gameMasterAID == null) {
                gameMasterAID = findGameMaster();
                if (gameMasterAID == null) {
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {
                    }
                }
            }
            sendJoinRequest();
            addBehaviour(new WaitForConfirmBehaviour());
        }
    }
    /**
     * Invia un messaggio di richiesta JOIN al GameMaster specificando il numero di giocatori desiderati.
     */
    private void sendJoinRequest() {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(gameMasterAID);
        msg.setContent(Messages.JOIN + ":" + requestedPlayers);
        send(msg);
        System.out.println(nickname + " inviata richiesta JOIN (partita da " + requestedPlayers + ")");
    }
    /**
     * In attesa di una conferma o di un rifiuto da parte del GameMaster per l'ingresso nella partita.
     */
    private class WaitForConfirmBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.or(
                    MessageTemplate.MatchPerformative(ACLMessage.CONFIRM),
                    MessageTemplate.MatchPerformative(ACLMessage.REFUSE));
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                if (msg.getPerformative() == ACLMessage.CONFIRM) {
                    System.out.println(nickname + " registrato!");
                    myAgent.removeBehaviour(this);
                    addBehaviour(new MainListenerBehaviour());
                } else {
                    view.showError("Lobby piena.");
                }
            } else {
                block();
            }
        }
    }

    /**
     * Comportamento principale dell'agente. Smista tutti i messaggi in arrivo
     * ai rispettivi gestori (GameMaster, HandManager o KittenDefense).
     */
    private class MainListenerBehaviour extends CyclicBehaviour {
        private boolean queryingForMaster = false;
        private boolean handReady = false;
        private boolean yourTurnPending = false;

        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg != null) {
                String content = msg.getContent();
                AID sender = msg.getSender();

                if (content.equals(Messages.NEW_MASTER)) {
                    handleFailover(sender);
                    return;
                }

                if (sender.equals(gameMasterAID)) {
                    dispatchFromGameMaster(content);
                } else if (sender.getLocalName().equals(handManagerAID.getLocalName())) {
                    dispatchFromHandManagerAgent(content);
                } else if (sender.getLocalName().equals(kittenDefenseAID.getLocalName())) {
                    dispatchFromKittenDefenseAgent(content);
                }
            } else {
                block();
            }
        }
        /**
         * Gestisce la caduta del Master primario e l'attivazione del BackupMaster.
         * @param newMaster L'AID del nuovo agente Master.
         */
        private void handleFailover(AID newMaster) {
            gameMasterAID = newMaster;
            System.out.println("[FAILOVER] Nuovo Master: " + gameMasterAID.getLocalName());
            view.showError("Master primario caduto. Backup attivato!");

            if (!gameStarted) {
                sendJoinRequest();
            } else {
                sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);
            }
        }

        //TODO Controllare, mi sa che ci siamo perse degli if con il refactor
        /**
         * Gestisce la logica di gioco proveniente dal GameMaster (turni, pescate, effetti carte).
         * @param content Il contenuto del messaggio ricevuto.
         */
        private void dispatchFromGameMaster(String content) {
            if (content.startsWith(Messages.PLAYER_LIST)) {
                updatePlayersUI(content);
            } else if (content.startsWith(Messages.HAND_INIT)) {
                gameStarted = true;
                processHandInit(content);
            } else if (content.equals(Messages.YOUR_TURN)) {
                view.showYourTurn();
                if (handReady) sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);
                else yourTurnPending = true;
            } else if (content.startsWith(Messages.TURN_OF)) {
                String otherPlayer = content.substring(Messages.TURN_OF.length());
                yourTurnPending = false;
                view.showOtherPlayerTurn(otherPlayer);
            } else if (content.equals(Messages.DREW_KITTEN)) {
                view.showExplosion();
                sendMsgToSubAgent(kittenDefenseAID, ACLMessage.INFORM, Messages.KITTEN_DRAWN);
            } else if (content.equals(Messages.DREW)) {
                String cardType = content.substring(Messages.DREW.length());
                view.showCardDrawn(cardType);
            } else if (content.startsWith(Messages.ADD_CARD) || content.startsWith(Messages.REMOVE_CARD)) {
                sendMsgToSubAgent(handManagerAID, ACLMessage.INFORM, content);
            } else if (content.startsWith(Messages.REFRESH_HAND)) {
                queryingForMaster = false;
                sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);
            } else if (content.startsWith(Messages.SEE_THE_FUTURE)) {
                String[] cards = content.substring(Messages.SEE_THE_FUTURE.length()).split(",");
                view.showSeeTheFuture(Arrays.asList(cards));
            } else if (content.startsWith(Messages.YOU_STOLE)) {
                String stolenType = content.substring(Messages.YOU_STOLE.length());
                view.showCardPlayed(nickname, String.valueOf(CardType.CAT_CARD));
                view.showCardDrawn(stolenType);
            } else if (content.startsWith(Messages.STOLEN_FROM_YOU)) {
                String stolenType = content.substring(Messages.STOLEN_FROM_YOU.length());
                view.showStolenCard(stolenType);
            } else if (content.startsWith(Messages.DEFUSED)) {
                view.showDefuseUsed();
            } else if (content.equals(Messages.SKIP_OK)) {
                view.showCardPlayed(nickname, String.valueOf(CardType.SKIP));
            } else if (content.equals(Messages.ATTACK_OK)) {
                view.showCardPlayed(nickname, String.valueOf(CardType.ATTACK));
            } else if (content.equals(Messages.SHUFFLE_OK)) {
                view.showShuffled();
            } else if (content.equals(Messages.CARD_NOT_IN_HAND)) { //TODO Non dovrebbe più servire dopo refactor view
                view.showCardNotInHand();
                sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);
            } else if (content.equals(Messages.NOT_YOUR_TURN)) {
                view.showNotYourTurn();
            } else if (content.equals(Messages.MISSING_TARGET)
                    || content.equals(Messages.INVALID_TARGET)) {
                view.showError("Target non valido.");
                sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);
            } else if (content.startsWith(Messages.WINNER)) {
                view.showGameOver(content.substring(Messages.WINNER.length()));
            } else if (content.equals(Messages.REQUEST_HAND)) {
                queryingForMaster = true;
                sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);
            } else if (content.equals(Messages.ASK_DEFUSE_POSITION)) {
                askDefusePosition();
            } else {
                view.showError(content);
            }

        }
        /**
         * Aggiorna la lista laterale dei giocatori nella View.
         * @param content Stringa contenente la lista dei nomi dei giocatori.
         */
        private void updatePlayersUI(String content) {
            String[] names = content.substring(Messages.PLAYER_LIST.length()).split(",");
            List<String> cleanNames = Arrays.stream(names)
                    .map(n -> n.replace("Player_", ""))
                    .filter(n -> !n.isBlank())
                    .toList();
            view.updatePlayersList(cleanNames);
        }
        /**
         * Gestisce la distribuzione iniziale della mano e avvia il thread per l'ascolto delle azioni utente.
         * @param content Stringa contenente i dati della mano iniziale.
         */
        private void processHandInit(String content) {
            sendMsgToSubAgent(handManagerAID, ACLMessage.INFORM, content);
            String handData = content.substring(Messages.HAND_INIT.length());
            view.showHand(parseHand(handData));

            new Thread(() -> {
                String action = view.askAction();
                ACLMessage toGM = new ACLMessage(ACLMessage.REQUEST);
                toGM.addReceiver(gameMasterAID);
                toGM.setContent(action);
                myAgent.send(toGM);
            }).start();
        }
        /**
         * Avvia un thread separato per richiedere all'utente la posizione del mazzo
         * in cui reinserire l'Exploding Kitten dopo un Defuse.
         */
        private void askDefusePosition() {
            new Thread(() -> {
                int position = view.askDefusePosition(0);
                ACLMessage defuseMsg = new ACLMessage(ACLMessage.REQUEST);
                defuseMsg.addReceiver(gameMasterAID);
                defuseMsg.setContent(Messages.DEFUSE_PLAY + position);
                myAgent.send(defuseMsg);
            }).start();
        }
        /**
         * Gestisce i messaggi provenienti dall'agente HandManager.
         * @param content Il contenuto del messaggio.
         */
        private void dispatchFromHandManagerAgent(String content) {
            if (content.equals(Messages.HAND_READY)) {
                handReady = true;
                if (yourTurnPending) {
                    yourTurnPending = false;
                    sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);
                }
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

                    new Thread(() -> {
                        String input = view.askAction();
                        ACLMessage toGM = new ACLMessage(ACLMessage.REQUEST);
                        toGM.addReceiver(gameMasterAID);
                        toGM.setContent(input);
                        myAgent.send(toGM);
                    }).start();
                }
            }
        }
        /**
         * Gestisce i messaggi provenienti dall'agente KittenDefense.
         * @param content Il contenuto del messaggio.
         */
        private void dispatchFromKittenDefenseAgent(String content) {
            if (content.equals(Messages.SHOW_ELIMINATED)) {
                view.showYouAreEliminated();
            }
            else if (content.equals(Messages.ASK_DEFUSE_POSITION)) {
                askDefusePosition();
            }
            else if (content.equals(Messages.REFRESH_HAND_AFTER_DEFUSE)) {
                sendMsgToSubAgent(handManagerAID, ACLMessage.REQUEST, Messages.GET_HAND);
            }
            else if (content.equals(Messages.PLAYER_ELIMINATED)) {
                System.out.println("[DEBUG] Giocatore eliminato. Notifico il Master.");
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(gameMasterAID);
                msg.setContent(Messages.PLAYER_ELIMINATED);
                send(msg);
            }
            else {
                ACLMessage toGM = new ACLMessage(ACLMessage.REQUEST);
                toGM.addReceiver(gameMasterAID);
                toGM.setContent(content);
                send(toGM);
            }
        }
    }

    // --- UTILS ---
    /**
     * Ricerca il GameMaster primario all'interno del DF.
     * @return L'AID del GameMaster se trovato, null altrimenti.
     */
    private AID findGameMaster() {
        try {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("game-master");
            template.addServices(sd);
            DFAgentDescription[] result = DFService.search(this, template);
            return (result.length > 0) ? result[0].getName() : null;
        } catch (Exception e) {
            return null;
        }
    }
    /**
     * Helper per l'invio rapido di messaggi ai sotto-agenti locali.
     * @param target L'AID del destinatario.
     * @param performative La performativa FIPA (es. ACLMessage.INFORM).
     * @param content Il contenuto testuale.
     */
    private void sendMsgToSubAgent(AID target, int performative, String content) {
        ACLMessage msg = new ACLMessage(performative);
        msg.addReceiver(target);
        msg.setContent(content);
        send(msg);
    }
    /**
     * Converte la stringa serializzata della mano in una lista di nomi di carte.
     * @param serialized Stringa con nomi di carte separati da virgola.
     * @return Lista di stringhe rappresentanti le carte.
     */
    private List<String> parseHand(String serialized) {
        if (serialized == null || serialized.isEmpty()) return new ArrayList<>();
        return Arrays.stream(serialized.split(",")).map(String::trim).toList();
    }
}