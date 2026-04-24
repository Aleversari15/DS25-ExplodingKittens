package explodingkittens;

import explodingkittens.game.view.SetupView;
import explodingkittens.remote.Messages;
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
            setupView.setAsHost(false);

            setupView.addStartListener(e -> {
                String nickname = setupView.getPlayerName();
                if (nickname.isEmpty()) return;

                setupView.setStartButtonEnabled(false);

                new Thread(() -> {
                    setupView.showStatusMessage("Stiamo controllando il nickname e la lobby...");
                    String result = checkNickAndLobbyWithServer(nickname);

                    if (result.equals(Messages.VALID_HOST)) {
                        SwingUtilities.invokeLater(() -> {
                            setupView.showStatusMessage("");
                            setupView.setAsHost(true);
                            setupView.setStartButtonEnabled(true);
                            setupView.updateButtonText("AVVIA PARTITA");

                            setupView.removeListeners();
                            setupView.addStartListener(ev -> {
                                int count = setupView.getPlayerCount();
                                setupView.close();
                                startJadeAgent(nickname, count, true);
                            });
                        });
                    } else if (result.equals(Messages.VALID_GUEST)) {
                        SwingUtilities.invokeLater(() -> {
                            setupView.showStatusMessage("");
                            setupView.close();
                            startJadeAgent(nickname, 0, false);
                        });
                    } else if (result.equals(Messages.LOBBY_FULL)) {
                        SwingUtilities.invokeLater(() -> {
                            setupView.showStatusMessage("");
                            setupView.showNicknameError("La lobby è piena! Impossibile partecipare.");
                            setupView.setStartButtonEnabled(true);
                        });
                    }else if (result.equals(Messages.INVALID_NICKNAME)){
                        SwingUtilities.invokeLater(() -> {
                            setupView.showStatusMessage("");
                            setupView.showNicknameError("Nickname già in uso! Scegline un altro.");
                            setupView.setStartButtonEnabled(true);
                        });
                    }
                    else{
                        SwingUtilities.invokeLater(() -> {
                            setupView.showStatusMessage("");
                            setupView.showNicknameError("Errore: " + result);
                            setupView.setStartButtonEnabled(true);
                        });
                    }
                }).start();
            });
        });
    }
    private static void startJadeAgent(String nickname, int requestedPlayers, boolean isHost) {
        try {
            Runtime rt = Runtime.instance();
            Profile profile = new ProfileImpl();
            profile.setParameter(Profile.MAIN, "false");
            profile.setParameter(Profile.MAIN_HOST, "localhost");

            AgentContainer container = rt.createAgentContainer(profile);
            AgentController player = container.createNewAgent(
                    nickname,
                    "explodingkittens.remote.PlayerAgent",
                    // Passiamo anche isHost all'agente
                    new Object[]{ nickname, requestedPlayers, isHost }
            );

            player.start();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static String checkNickAndLobbyWithServer(String nickname) {
        try {
            BlockingQueue<String> queue = new ArrayBlockingQueue<>(1);
            Runtime rt = Runtime.instance();
            Profile profile = new ProfileImpl();
            profile.setParameter(Profile.MAIN, "false");
            profile.setParameter(Profile.MAIN_HOST, "localhost");

            AgentContainer container = rt.createAgentContainer(profile);

            AgentController checker = container.createNewAgent(
                    "Checker_" + System.currentTimeMillis(),
                    "explodingkittens.remote.NicknameCheckerAgent",
                    new Object[]{nickname, queue}
            );

            checker.start();
            String result = queue.take();

            container.kill();

            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
    }
}