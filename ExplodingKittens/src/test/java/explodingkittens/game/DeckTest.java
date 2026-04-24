package explodingkittens.game;


import explodingkittens.game.model.Card;
import explodingkittens.game.model.CardType;
import explodingkittens.game.model.Deck;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.ArrayList;

/**
 * Classe di test per la gestione del mazzo (Deck).
 * Verifica il corretto inserimento, rimozione e manipolazione delle carte.
 */
public class DeckTest {

    private static final int ON_TOP = 0;
    private static final int SEE_THE_FUTURE_CARDS = 3;
    private Deck deck;
    private Card skipCard;
    private Card attackCard;

    /**
     * Inizializza l'ambiente di test prima di ogni esecuzione.
     */
    @BeforeEach
    void setUp() {
        deck = new Deck();
        skipCard = new Card(CardType.SKIP, "Skip", "End your turn without playing a card.");
        attackCard = new Card(CardType.ATTACK, "Attack", "End your turn and force the next player to take two turns.");
    }

    /**
     * Testa l'inserimento di una carta in una posizione specifica del mazzo.
     */
    @Test
    void testInsertCard() {
        int initialSize = deck.size();
        deck.insertCard(skipCard, 0);
        assertEquals(initialSize + 1, deck.size(), "La dimensione del mazzo deve aumentare di 1");

        //  controlliamo se contiene la carta che abbiamo inserito
        boolean containsCard = deck.getCards().contains(skipCard);
        assertTrue(containsCard, "Il mazzo deve contenere la carta appena inserita");
    }

    /**
     * Testa il pescaggio di una carta dalla cima del mazzo.
     */
    @Test
    void testDrawCard() {
        deck.insertCard(skipCard, 0);
        deck.insertCard(attackCard, 0);
        int initialSize = deck.size();
        Card drawnCard = deck.removeTopCard();
        assertEquals(initialSize - 1, deck.size(), "La dimensione del mazzo deve diminuire di 1 dopo il pescaggio");
        assertSame(drawnCard.getType(), attackCard.getType(), "La carta pescata deve essere tra quelle inserite");
    }

    /**
     * Testa il tentativo di pescare una carta da un mazzo vuoto e verifica che venga restituito null.
     */
    @Test
    void testDrawFromEmptyDeck() {
        Card drawnCard = deck.removeTopCard();
        assertNull(drawnCard, "Pescare da un mazzo vuoto deve restituire null");
    }

    /**
     * Testa il rimpiazzo e l'aggiornamento dell'intero mazzo tramite una lista di carte.
     */
    @Test
    void testSetCards() {
        List<Card> newCards = new ArrayList<>();
        newCards.add(skipCard);
        newCards.add(attackCard);

        deck.setCards(newCards);

        assertEquals(2, deck.size(), "Il mazzo deve contenere esattamente 2 carte");

        boolean hasSkip = false;
        boolean hasAttack = false;
        for(Card c : deck.getCards()){
            if(c.getType() == CardType.SKIP) hasSkip = true;
            if(c.getType() == CardType.ATTACK) hasAttack = true;
        }
        assertTrue(hasSkip, "Il mazzo deve contenere la carta Skip");
        assertTrue(hasAttack, "Il mazzo deve contenere la carta Attack");
    }

    /**
     * Testa la funzionalità di "See the Future" quando il mazzo contiene meno carte di quelle che si vorrebbero vedere.
     */
    @Test
    void testSeeTheFutureWithFewCards() {
        deck.insertCard(attackCard, ON_TOP);
        deck.insertCard(skipCard, ON_TOP);

        int actualPeek = Math.min(SEE_THE_FUTURE_CARDS, deck.size());

        List<Card> futureCards = deck.peekTop(actualPeek);
        assertEquals(2, futureCards.size(), "Deve vedere solo le 2 carte presenti");
        assertEquals(CardType.SKIP, futureCards.get(ON_TOP).getType(), "La prima carta deve essere lo Skip (in cima)");
    }


}
