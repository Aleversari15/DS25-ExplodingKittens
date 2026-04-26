package explodingkittens.remote;

import jade.core.AID;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

/**
 * Agente master primario. Gestisce la lobby, avvia la partita
 * e invia heartbeat periodici al BackupMasterAgent.
 * Tutta la logica condivisa è in AbstractMasterAgent.
 */
public class GameMasterAgent extends AbstractMasterAgent {
    private AID backupMasterAID;

    @Override
    protected void setup() {
        registerInDF();
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            expectedPlayers = Integer.parseInt(args[0].toString());
        }
        gameState = new explodingkittens.game.model.GameState();
        deck = new explodingkittens.game.model.Deck();
        startHeartbeat();
        System.out.println("[GameMaster] Avviato, aspetto " + expectedPlayers + " giocatori...");
        lobbyBehaviour = new LobbyBehaviour();
        addBehaviour(lobbyBehaviour);
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (Exception ignored) {}
    }

    /**
     * dopo ogni JOIN sincronizza subito lo stato col BackupMaster
     * senza aspettare il prossimo tick del heartbeat periodico.
     */
    @Override
    protected void onPlayerJoined() {
        sincronize();
    }

    /**
     * Avvia l'invio di heartbeat periodici al BackupMaster.
     * Al primo tick cerca il backup nel DF; se non lo trova riprova al tick successivo.
     * Appena il backup è disponibile invia anche un heartbeat immediato.
     */
    private void startHeartbeat() {
        addBehaviour(new TickerBehaviour(this, 3000) {
            @Override
            protected void onTick() {
                if (backupMasterAID == null) backupMasterAID = findBackup();
                if (backupMasterAID != null) sendHeartbeat();
            }
        });

        addBehaviour(new TickerBehaviour(this, 3000) {
            @Override
            protected void onTick() {
                if (backupMasterAID == null) backupMasterAID = findBackup();
                if (backupMasterAID != null) sendHeartbeat();
            }
        });
    }

    /**
     * Sincronizzazione manuale immediata dello stato col BackupMaster.
     * Chiamata ogni volta che lo stato cambia (es. nuovo player in lobby).
     */
    private void sincronize() {
        if (backupMasterAID == null) backupMasterAID = findBackup();
        if (backupMasterAID != null) sendHeartbeat();
    }

    private void sendHeartbeat() {
        ACLMessage hb = new ACLMessage(ACLMessage.INFORM);
        hb.addReceiver(backupMasterAID);
        hb.setContent(Messages.HEARTBEAT + ":" + serializeState());
        send(hb);
    }

    private AID findBackup() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("backup-master");
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) return result[0].getName();
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    private void registerInDF() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("game-master");
            sd.setName("exploding-kittens");
            dfd.addServices(sd);
            DFService.register(this, dfd);
        } catch (Exception e) { e.printStackTrace(); }
    }
}