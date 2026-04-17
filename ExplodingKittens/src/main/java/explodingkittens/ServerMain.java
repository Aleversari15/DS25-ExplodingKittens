package explodingkittens;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

public class ServerMain {
    public static void main(String[] args) throws Exception {

        Runtime rt = Runtime.instance();
        Profile profile = new ProfileImpl();

        profile.setParameter(Profile.MAIN, "true");
        profile.setParameter(Profile.GUI, "true");

        AgentContainer mainContainer = rt.createMainContainer(profile);

        AgentController gameMaster = mainContainer.createNewAgent(
                "GameMaster",
                "explodingkittens.remote.GameMasterAgent",
                new Object[]{ }
        );

        gameMaster.start();
        System.out.println("Server avviato (GameMaster)");
    }
}
