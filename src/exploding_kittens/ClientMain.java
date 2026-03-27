package exploding_kittens;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

public class ClientMain {
    public static void main(String[] args) throws Exception {

        Runtime rt = Runtime.instance();
        Profile profile = new ProfileImpl();

        profile.setParameter(Profile.MAIN, "false");
        profile.setParameter(Profile.MAIN_HOST, "localhost"); //TODO: sostituire con ip server se usiamo macchine diverse

        AgentContainer container = rt.createAgentContainer(profile);

        String nickname = args.length > 0 ? args[0] : "Player";

        AgentController player = container.createNewAgent(
                "Player_" + nickname,
                "exploding_kittens.remote.PlayerAgent",
                new Object[]{ nickname }
        );

        player.start();

        System.out.println("Client avviato: " + nickname);
    }
}
