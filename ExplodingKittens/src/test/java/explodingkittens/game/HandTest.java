package explodingkittens.game;

import explodingkittens.game.model.Card;
import explodingkittens.game.model.CardType;
import explodingkittens.game.model.Hand;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Classe di test per la gestione della mano (Hand).
 * Verifica il corretto inserimento, rimozione e ricerca delle carte.
 */
public class HandTest {

    private Hand hand;
    private Card defuseCard;
    private Card catCard;

    /**
     * Inizializza l'ambiente di test prima di ogni esecuzione.
     */
    @BeforeEach
    void setUp() {
        hand = new Hand();
        defuseCard = new Card(CardType.DEFUSE, "Defuse", "Neutralize an Exploding Kitten and prevent yourself from exploding.");
        catCard = new Card(CardType.CAT_CARD, "Cat Card", "A cute cat card with no special abilities.");
    }

    /**
     * Testa l'aggiunta di una carta alla mano.
     */
    @Test
    void testAddCard() {
        int initialSize = hand.size();
        hand.addCard(defuseCard);
        assertEquals(initialSize + 1, hand.size(), "La dimensione deve aumentare di 1.");
        assertTrue(hand.getCards().contains(defuseCard), "La mano deve contenere la carta appena aggiunta.");
    }

    /**
     * Testa la rimozione di una carta precedentemente inserita.
     */
    @Test
    void testRemoveCard() {
        hand.addCard(defuseCard);
        int initialSize = hand.size();
        hand.removeCard(defuseCard);
        assertEquals(initialSize - 1, hand.size(), "La dimensione deve diminuire di 1.");
        assertFalse(hand.getCards().contains(defuseCard), "La carta non deve essere più presente.");
    }

    /**
     * Testa l'individuazione di una carta in base al suo tipo.
     */
    @Test
    void testHasCardOfType() {
        hand.addCard(defuseCard);
        assertTrue(hand.hasCardOfType(CardType.DEFUSE), "Deve essere true per DEFUSE.");
        assertFalse(hand.hasCardOfType(CardType.CAT_CARD), "Deve essere false per CAT_CARD.");
    }


}
