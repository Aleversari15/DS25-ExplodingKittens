package exploding_kittens;

import exploding_kittens.game.view.SetupView;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

import javax.swing.*;

public class ClientMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SetupView setupView = new SetupView();

            setupView.addStartListener(e -> {
                String nickname = setupView.getPlayerName();
                int playerCount = setupView.getPlayerCount();

                setupView.close();

                new Thread(() -> startJadeAgent(nickname, playerCount)).start();
            });
        });
    }
    private static void startJadeAgent(String nickname, int requestedPlayers) {
        try {
            Runtime rt = Runtime.instance();
            Profile profile = new ProfileImpl();
            profile.setParameter(Profile.MAIN, "false");
            profile.setParameter(Profile.MAIN_HOST, "localhost"); // IP del server

            AgentContainer container = rt.createAgentContainer(profile);

            AgentController player = container.createNewAgent(
                    "Player_" + nickname,
                    "exploding_kittens.remote.PlayerAgent",
                    new Object[]{ nickname, requestedPlayers }
            );

            player.start();
            System.out.println("Client avviato: " + nickname + " (Richiesti " + requestedPlayers + " giocatori)");

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}

