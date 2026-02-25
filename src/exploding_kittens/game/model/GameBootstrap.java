package exploding_kittens.game.model;

import java.util.Collections;
import java.util.List;

public class GameBootstrap {
    private final List<Player> players;
    private final Deck deck;

    public GameBootstrap(List<Player> players, Deck deck) {
        this.players = players;
        this.deck = deck;
    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }
    public Deck getDeck() {
        return deck;
    }
}