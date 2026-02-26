package exploding_kittens.game.model;

import java.util.ArrayList;
import java.util.List;

//inizializza il gioco e crea lo stato che gli agenti useranno
public class GameBootstrap {

    private final List<Player> players;
    private final Deck deck;

    public GameBootstrap(List<Player> players) {
        this.players = new ArrayList<>(players);
        this.deck = new Deck();
        initializeGame();
    }

    private void initializeGame() {
        List<Card> cards = CardFactory.createStandardDeck(players.size());
        for (Card card : cards) {
            deck.addCard(card);
        }
        deck.shuffle();
    }

    public GameState buildGame () {
            return new GameState(deck, players);
    }

}