package tests;

import exploding_kittens.game.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CardFactoryTest {

    @Test
    void testDeckSize_2Players() {
        // (2-1) EK + (2+2) DEFUSE + 4 SKIP + 4 ATTACK + 4 SHUFFLE + 4 SEE + 8 CAT = 1+4+4+4+4+4+8 = 29
        List<Card> deck = CardFactory.createStandardDeck(2);
        assertEquals(29, deck.size());
    }

    @Test
    void testDeckSize_4Players() {
        // (4-1) EK + (4+2) DEFUSE + 4+4+4+4 azione + 8 CAT = 3+6+16+8 = 33
        List<Card> deck = CardFactory.createStandardDeck(4);
        assertEquals(33, deck.size());
    }

    @Test
    void testActionCardsCount() {
        List<Card> deck = CardFactory.createStandardDeck(3);
        Map<CardType, Long> countByType = deck.stream()
                .collect(Collectors.groupingBy(Card::getType, Collectors.counting()));

        assertEquals(4, countByType.get(CardType.SKIP));
        assertEquals(4, countByType.get(CardType.ATTACK));
        assertEquals(4, countByType.get(CardType.SHUFFLE));
        assertEquals(4, countByType.get(CardType.SEE_THE_FUTURE));
    }

    @Test
    void testCatCardCount() {
        List<Card> deck = CardFactory.createStandardDeck(3);
        long count = deck.stream()
                .filter(c -> c.getType() == CardType.CAT_CARD)
                .count();
        assertEquals(8, count);
    }

    @Test
    void testDefuseCount() {
        int playerCount = 3;
        List<Card> deck = CardFactory.createStandardDeck(playerCount);
        long count = deck.stream()
                .filter(c -> c.getType() == CardType.DEFUSE)
                .count();
        assertEquals(playerCount + 2, count);
    }

    @Test
    void testExplodingKittenCount() {
        int playerCount = 3;
        List<Card> deck = CardFactory.createStandardDeck(playerCount);
        long count = deck.stream()
                .filter(c -> c.getType() == CardType.EXPLODING_KITTEN)
                .count();
        assertEquals(playerCount - 1, count);
    }

}