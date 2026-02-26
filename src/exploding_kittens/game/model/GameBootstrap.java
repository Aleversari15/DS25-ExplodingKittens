package exploding_kittens.game.model;

import java.util.Collections;
import java.util.List;

//inizializza il gioco e crea lo stato che gli agenti useranno
public class GameBootstrap {
    private final List<Player> players;
    private final Deck deck;

    public GameBootstrap(List<Player> players, Deck deck) {
        this.players = new ArrayList<>(players);
        this.deck = new Deck();
        initializeGame();
    }

    private void initializeGame(){

    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }
    public Deck getDeck() {
        return deck;
    }
}