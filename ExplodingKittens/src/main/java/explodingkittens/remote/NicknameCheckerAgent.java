package explodingkittens.remote;

import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import java.util.concurrent.BlockingQueue;

/**
 * Agente specializzato nella validazione pre-ingresso dei client.
 * Questo agente funge da intermediario sincrono tra l'interfaccia di setup e il GameMaster.
 * Esegue una verifica atomica che comprende:
 * 1)La disponibilità del nickname scelto (evitando duplicati).
 * 2)Lo stato attuale della lobby per determinare il ruolo del client (Host o Guest).
 * Comunica il risultato al thread chiamante attraverso una {@link BlockingQueue},
 * restituendo codici di stato predefiniti (es. VALID_HOST, VALID_GUEST, INVALID).
 */
public class NicknameCheckerAgent extends Agent {

    @Override
    protected void setup() {
        String nickname = (String) getArguments()[0];
        BlockingQueue<String> resultQueue = (BlockingQueue<String>) getArguments()[1];

        AID gameMasterAID = findGameMaster();

        if (gameMasterAID == null) {
            resultQueue.offer("ERROR_NO_SERVER");
            doDelete();
            return;
        }
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(gameMasterAID);
        msg.setContent(Messages.NICKNAME_AND_LOBBY_CHECK + nickname);
        send(msg);

        ACLMessage reply = blockingReceive(3000);

        if (reply != null) {
            resultQueue.offer(reply.getContent());  // Il contenuto sarà "VALID_HOST", "VALID_GUEST" o "INVALID"
        } else {
            resultQueue.offer("ERROR_TIMEOUT");
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
