package tests;

import exploding_kittens.game.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HandTest {

    private Hand hand;
    private Card defuseCard;
    private Card catCard;

    @BeforeEach
    void setUp() {
        hand = new Hand();
        defuseCard = new Card(CardType.DEFUSE, "Defuse", "Neutralize an Exploding Kitten and prevent yourself from exploding.");
        catCard = new Card(CardType.CAT_CARD, "Cat Card", "A cute cat card with no special abilities.");
    }

    @Test
    void testAddCard() {
        int initialSize = hand.size();
        hand.addCard(defuseCard);
        assertEquals(initialSize + 1, hand.size());
        assertTrue(hand.getCards().contains(defuseCard));
    }

    @Test
    void testRemoveCard() {
        hand.addCard(defuseCard);
        int initialSize = hand.size();
        boolean removed = hand.removeCard(defuseCard);
        assertTrue(removed);
        assertEquals(initialSize - 1, hand.size());
        assertFalse(hand.getCards().contains(defuseCard));
    }

    @Test
    void testHasCardOfType() {
        hand.addCard(defuseCard);
        assertTrue(hand.hasCardOfType(CardType.DEFUSE));
        assertFalse(hand.hasCardOfType(CardType.CAT_CARD));
    }

    @Test
    void testRemoveNonexistentCard() {
        hand.addCard(defuseCard);
        int initialSize = hand.size();
        boolean removed = hand.removeCard(catCard);
        assertFalse(removed);
        assertEquals(initialSize, hand.size());
    }
}
