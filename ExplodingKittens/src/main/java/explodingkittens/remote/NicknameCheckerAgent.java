package explodingkittens.remote;

import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import java.util.concurrent.BlockingQueue;

/**
 * Agente creato per effettuare controlli prima di permettere all'utente di unirsi alla partita.
 * Al momento contatta il server per controllare se il nickname inserito dall'utente nella setUp
 * view è già utilizzato da uno dei giocatori registati o se è valido.
 */
public class NicknameCheckerAgent extends Agent {

    @Override
    protected void setup() {
        String nickname = (String) getArguments()[0];
        BlockingQueue<Boolean> result = (BlockingQueue<Boolean>) getArguments()[1];

        AID gameMasterAID = findGameMaster();
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(gameMasterAID);
        msg.setContent(Messages.NICKNAME_CHECK + nickname);
        send(msg);

        ACLMessage reply = blockingReceive(3000);

        if (reply != null && reply.getContent().equals(Messages.NICKNAME_OK)) {
            result.offer(true);
        } else {
            result.offer(false);
        }

        doDelete();
    }

    private AID findGameMaster() {
        try {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("game-master");
            template.addServices(sd);
            DFAgentDescription[] result = DFService.search(this, template);
            return result.length > 0 ? result[0].getName() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
