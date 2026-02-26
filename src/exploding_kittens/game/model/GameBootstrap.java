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
        populateDeck();
        deck.shuffle();
        //da ad ogni giocatore una carta Defuse e 7 carte casuali dal mazzo
        for (Player player : players) {
            player.addCardToHand(new Card(CardType.DEFUSE));
            for (int i = 0; i < INITIAL_HAND_SIZE; i++) {
                player.addCardToHand(deck.removeTopCard());
            }
        //rinserisce le carte Exploding Kitten nel mazzo, in numero pari al numero di giocatori - 1
        for (int i = 0; i < players.size() - 1; i++) {
            deck.addCard(new Card(CardType.EXPLODING_KITTEN));
        }
        deck.shuffle();
    }

    private void populateDeck() {
        for (int i = 0; i < 4; i++) deck.addCard(new Card(CardType.ATTACK));
        for (int i = 0; i < 4; i++) deck.addCard(new Card(CardType.SKIP));
        for (int i = 0; i < 4; i++) deck.addCard(new Card(CardType.SHUFFLE));
        for (int i = 0; i < 5; i++) deck.addCard(new Card(CardType.SEE_THE_FUTURE));
        for (int i = 0; i < 4; i++) deck.addCard(new Card(CardType.NOPE));
        for (int i = 0; i < 6; i++) deck.addCard(new Card(CardType.DEFUSE));
    }

    public GameState buildGame(){
        return new GameState(deck, players);
    }
}