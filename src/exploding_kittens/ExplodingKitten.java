package exploding_kittens;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import java.util.Scanner;
/**
 * Entry point del gioco.
 */
public class ExplodingKitten {

    public static void main(String[] args) throws Exception {

        // Avvia il runtime JADE
        Runtime rt = Runtime.instance();
        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.MAIN_HOST, "localhost");
        profile.setParameter(Profile.GUI, "true");

        AgentContainer mainContainer = rt.createMainContainer(profile);

        //Avvia il GameMasterAgent passandogli il numero di giocatori attesi
        AgentController gameMaster = mainContainer.createNewAgent(
                "GameMaster",
                "exploding_kittens.remote.GameMasterAgent",
                 new Object[]{ String.valueOf(2) } //TODO: sostituire con numPlayers dopo averlo letto da input
        );
        gameMaster.start();
        System.out.println("GameMasterAgent avviato.");

        Thread.sleep(500);

        //  Avvia un PlayerAgent per ogni giocatore
        //TODO: leggere i nickname da input invece di usare quelli hardcoded
        String[] nicknames = {"Player1", "Player2"};
        for (int i = 0; i < 2; i++) {
            AgentController player = mainContainer.createNewAgent(
                    "Player_" + nicknames[i],
                    "exploding_kittens.remote.PlayerAgent",
                    new Object[]{ nicknames[i] }
            );
            player.start();
            System.out.println("PlayerAgent avviato: " + nicknames[i]);
        }
    }
}