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

    public void addPlayer(Player player) { activePlayers.add(player); }

    public void removePlayer(Player player) { activePlayers.remove(player); }

    public Player getCurrentPlayer() { return activePlayers.get(currentPlayerIndex); }

    public List<Player> getActivePlayers() { return new ArrayList<>(activePlayers); }

    public void nextTurn() {
        turnsToPlay--;
        if (turnsToPlay <= 0) {
            currentPlayerIndex = (currentPlayerIndex + 1) % activePlayers.size();
            turnsToPlay = 1;
        }
    }

    public void setTurnsToPlay(int n) { this.turnsToPlay = n; }

    public int getTurnsToPlay() { return turnsToPlay; }

    public boolean isGameOver() { return activePlayers.size() == 1; }

    public Player getWinner() { return isGameOver() ? activePlayers.getFirst() : null; }
}
