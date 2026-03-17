package tests;

import exploding_kittens.game.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CardFactoryTest {

    private static final int TWO_PLAYERS = 2;
    private static final int FOUR_PLAYERS = 4;
    private static final int DECK_SIZE_2_PLAYERS = 29;
    private static final int DECK_SIZE_4_PLAYERS = 33;
    private static final int PLAYER_COUNT = 3;
    private static final int EXPECTED_SKIP_CARDS = 4;
    private static final int EXPECTED_ATTACK_CARDS = 4;
    private static final int EXPECTED_SHUFFLE_CARDS = 4;
    private static final int EXPECTED_SEE_CARDS = 4;
    private static final int EXPECTED_CAT_CARDS = 8;



    @Test
    void testDeckSize_2Players() {
        // (2-1) EK + (2+2) DEFUSE + 4 SKIP + 4 ATTACK + 4 SHUFFLE + 4 SEE + 8 CAT = 1+4+4+4+4+4+8 = 29
        List<Card> deck = CardFactory.createStandardDeck(TWO_PLAYERS);
        assertEquals(DECK_SIZE_2_PLAYERS, deck.size());
    }

    @Test
    void testDeckSize_4Players() {
        // (4-1) EK + (4+2) DEFUSE + 4+4+4+4 azione + 8 CAT = 3+6+16+8 = 33
        List<Card> deck = CardFactory.createStandardDeck(FOUR_PLAYERS);
        assertEquals(DECK_SIZE_4_PLAYERS, deck.size());
    }

    @Test
    void testActionCardsCount() {
        List<Card> deck = CardFactory.createStandardDeck(PLAYER_COUNT);
        Map<CardType, Long> countByType = deck.stream()
                .collect(Collectors.groupingBy(Card::getType, Collectors.counting()));

        assertEquals(EXPECTED_SKIP_CARDS, countByType.get(CardType.SKIP));
        assertEquals(EXPECTED_ATTACK_CARDS, countByType.get(CardType.ATTACK));
        assertEquals(EXPECTED_SHUFFLE_CARDS, countByType.get(CardType.SHUFFLE));
        assertEquals(EXPECTED_SEE_CARDS, countByType.get(CardType.SEE_THE_FUTURE));
    }

    @Test
    void testCatCardCount() {
        List<Card> deck = CardFactory.createStandardDeck(PLAYER_COUNT);
        long count = deck.stream()
                .filter(c -> c.getType() == CardType.CAT_CARD)
                .count();
        assertEquals(EXPECTED_CAT_CARDS , count);
    }

    @Test
    void testDefuseCount() {
        List<Card> deck = CardFactory.createStandardDeck(PLAYER_COUNT);
        long count = deck.stream()
                .filter(c -> c.getType() == CardType.DEFUSE)
                .count();
        assertEquals( PLAYER_COUNT + 2, count);
    }

    @Test
    void testExplodingKittenCount() {
        List<Card> deck = CardFactory.createStandardDeck(PLAYER_COUNT);
        long count = deck.stream()
                .filter(c -> c.getType() == CardType.EXPLODING_KITTEN)
                .count();
        assertEquals(PLAYER_COUNT - 1, count);
    }

}