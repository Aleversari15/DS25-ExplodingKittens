package explodingkittens.game;

import explodingkittens.game.model.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Classe di test per la gestione della mano (Hand).
 * Verifica il corretto inserimento, rimozione e ricerca delle carte.
 */
public class HandTest {

    private static final String DEFUSE = "DEFUSE";
    private Hand hand;
    private Card defuseCard;
    private Card catCard;

    /**
     * Inizializza l'ambiente di test prima di ogni esecuzione.
     */
    @BeforeEach
    void setUp() {
        hand = new Hand();
        defuseCard = new Card(CardType.DEFUSE, DEFUSE, "Neutralize an Exploding Kitten and prevent yourself from exploding.");
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

    /**
     * Testa che ogni mano dei giocatori contenga una carta DEFUSE.
     */
    @Test
    void testDefuseCardInHand() {
        int nPlayers = 3;
        Deck deck = DeckBuilder.prepareBaseDeck(nPlayers);
        List<Player> players = List.of(new Player("P1", "A1"), new Player("P2", "A2"), new Player("P3", "A3"));

        var hands = DeckBuilder.buildPlayerHands(deck, players);

        for(String hand : hands.values()) {
            assertTrue(hand.contains(DEFUSE), "Ogni mano deve contenere una carta DEFUSE.");
        }
    }


}
