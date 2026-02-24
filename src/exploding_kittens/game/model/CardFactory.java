package exploding_kittens.game.model;

import java.util.ArrayList;
import java.util.List;

public class CardFactory {

    public static List<Card> createStandardDeck(int playerCount) {
        List<Card> cards = new ArrayList<>();

        // Exploding Kittens: num giocatori - 1
        for (int i = 0; i < playerCount - 1; i++)
            cards.add(new Card(CardType.EXPLODING_KITTEN, "Exploding Kitten", "You explode unless you have a Defuse."));

        // Defuse: num giocatori  + qualcuno in più nel mazzo
        for (int i = 0; i < playerCount + 2; i++)
            cards.add(new Card(CardType.DEFUSE, "Defuse", "Neutralizes an Exploding Kitten."));

        /*---------------CARTE AZIONE--------------------*/
        for (int i = 0; i < 4; i++)
            cards.add(new Card(CardType.SKIP, "Skip", "End your turn without drawing."));
        for (int i = 0; i < 4; i++)
            cards.add(new Card(CardType.ATTACK, "Attack", "Force the next player to take two turns."));
        for (int i = 0; i < 4; i++)
            cards.add(new Card(CardType.SHUFFLE, "Shuffle", "Shuffle the draw pile."));
        for (int i = 0; i < 4; i++)
            cards.add(new Card(CardType.SEE_THE_FUTURE, "See the Future", "Peek at the top 3 cards."));

        // Carte Gatto: da utilizzare a coppie per applicare un effetto
        for (int i = 0; i < 8; i++)
            cards.add(new Card(CardType.CAT_CARD, "Cat Card", "Play as pairs to steal a random card."));

        return cards;
    }
}

