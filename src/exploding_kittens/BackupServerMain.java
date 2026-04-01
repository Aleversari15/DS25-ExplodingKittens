package exploding_kittens;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

public class BackupServerMain {
    public static void main(String[] args) {
        Runtime rt = Runtime.instance();

        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
        p.setParameter(Profile.CONTAINER_NAME, "Backup-Container");

        try {
            AgentContainer container = rt.createAgentContainer(p);

            AgentController backupMaster = container.createNewAgent(
                    "BackupMaster",
                    "exploding_kittens.remote.BackupMasterAgent",
                    null
            );

            backupMaster.start();
            System.out.println("--- Backup Server avviato e pronto al subentro ---");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
