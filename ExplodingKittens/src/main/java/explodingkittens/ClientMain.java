package explodingkittens;

import explodingkittens.game.view.SetupView;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

import javax.swing.*;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ClientMain {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SetupView setupView = new SetupView();

            setupView.addStartListener(e -> {

                setupView.setStartButtonEnabled(false);

                new Thread(() -> {

                    String nickname = setupView.getPlayerName();
                    System.out.println("NICKNAME SCELTO: " + nickname);

                    if (nickname.isEmpty()) {
                        SwingUtilities.invokeLater(() ->
                                setupView.setStartButtonEnabled(true));
                        return;
                    }

                    boolean valid = checkNicknameWithServer(nickname, setupView);
                    System.out.println("è valido?: " + valid);

                    if (valid) {

                        int playerCount = setupView.getPlayerCount();

                        SwingUtilities.invokeLater(() -> {
                            setupView.close();
                            startJadeAgent(nickname, playerCount);
                        });

                    } else {

                        SwingUtilities.invokeLater(() -> {
                            setupView.showNicknameError("Nickname già in uso. Scegline un altro.");
                            setupView.setStartButtonEnabled(true);
                        });
                    }

                }).start();
            });
        });
    }

    private static void startJadeAgent(String nickname, int requestedPlayers) {
        try {
            Runtime rt = Runtime.instance();
            Profile profile = new ProfileImpl();
            profile.setParameter(Profile.MAIN, "false");
            profile.setParameter(Profile.MAIN_HOST, "localhost"); // IP del server

            AgentContainer container= rt.createAgentContainer(profile);
            AgentController player = container.createNewAgent(
                    "Player_" + nickname,
                    "explodingkittens.remote.PlayerAgent",
                    new Object[]{ nickname, requestedPlayers }
            );

            player.start();
            System.out.println("Client avviato: " + nickname + " (Richiesti " + requestedPlayers + " giocatori)");

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static boolean checkNicknameWithServer(String nickname, SetupView view) {
        try {
            BlockingQueue<Boolean> queue = new ArrayBlockingQueue<>(1);
            Runtime rt = Runtime.instance();
            Profile profile = new ProfileImpl();
            profile.setParameter(Profile.MAIN, "false");
            profile.setParameter(Profile.MAIN_HOST, "localhost");

             AgentContainer container= rt.createAgentContainer(profile);

            AgentController checker = container.createNewAgent(
                    "Checker_" + + System.currentTimeMillis(),
                    "explodingkittens.remote.NicknameCheckerAgent",
                    new Object[]{nickname, queue}
            );

            checker.start();
            boolean result = queue.take();
            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}

