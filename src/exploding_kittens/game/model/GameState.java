package exploding_kittens.game.model;

import java.util.ArrayList;
import java.util.List;

public class GameState {
    private final List<Player> activePlayers;
    private int currentPlayerIndex;
    private int turnsToPlay; //Serve per gestire i turni quando vengono giocate carte Attack

    public GameState() {
        this.activePlayers = new ArrayList<>();
        this.currentPlayerIndex = 0;
        this.turnsToPlay = 1;
    }

    public void addPlayer(Player player) {
        activePlayers.add(player);
    }

    public void removePlayer(Player player) {
        int indexToRemove = -1;
        for (int i = 0; i < activePlayers.size(); i++) {
            if (activePlayers.get(i).getAgentName().equals(player.getAgentName())) {
                indexToRemove = i;
                break;
            }
        }
        activePlayers.remove(player);
        if (indexToRemove < currentPlayerIndex) {
            currentPlayerIndex--;
        }
        if (currentPlayerIndex >= activePlayers.size() && !activePlayers.isEmpty()) {
            currentPlayerIndex = 0;
        }
    }

    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }

    public Player getCurrentPlayer() {
        return activePlayers.get(currentPlayerIndex);
    }

    public List<Player> getActivePlayers() {
        return new ArrayList<>(activePlayers);
    }

    public void nextTurn() {
        turnsToPlay--;
        if (turnsToPlay <= 0) {
            currentPlayerIndex = (currentPlayerIndex + 1) % activePlayers.size();
            turnsToPlay = 1;
        }
    }

    public void setTurnsToPlay(int n) {
        this.turnsToPlay = n;
    }

    public int getTurnsToPlay() {
        return turnsToPlay;
    }

    public boolean isGameOver() {
        return activePlayers.size() == 1;
    }

    public Player getWinner() {
        return isGameOver() ? activePlayers.getFirst() : null;
    }

    public void setCurrentPlayerIndex(int index) {
        if (!activePlayers.isEmpty()) {
            this.currentPlayerIndex = index % activePlayers.size();
        }
    }

    public void setActivePlayers(List<Player> players) {
        this.activePlayers.clear();
        this.activePlayers.addAll(players);
    }
}
