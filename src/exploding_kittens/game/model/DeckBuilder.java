package exploding_kittens.game.model;

import java.util.Collections;
import java.util.List;

/**
 * Utility class per la preparazione iniziale del mazzo.
 */
public class DeckBuilder {

    private DeckBuilder() {}

    public static Deck prepareBaseDeck(int numPlayers) {
        List<Card> cards = CardFactory.createStandardDeck(numPlayers);
        cards.removeIf(c -> c.getType() == CardType.EXPLODING_KITTEN);
        cards.removeIf(c -> c.getType() == CardType.DEFUSE);
        Collections.shuffle(cards);

        Deck deck = new Deck();
        deck.setCards(cards);
        return deck;
    }

}