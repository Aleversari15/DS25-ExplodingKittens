package explodingkittens;

import explodingkittens.game.model.*;


public class DeckTest {

  /*  private Deck deck;
    private Card skipCard;
    private Card attackCard;

    @BeforeEach
    void setUp() {
        deck = new Deck();
        skipCard = new Card(CardType.SKIP, "Skip", "End your turn without playing a card.");
        attackCard = new Card(CardType.ATTACK, "Attack", "End your turn and force the next player to take two turns.");
    }

    @Test
    void testAddCard() {
        int initialSize = deck.size();
        deck.addCard(skipCard);
        assertEquals(initialSize + 1, deck.size());
        assertTrue(deck.getCards().contains(skipCard));
    }

    @Test
    void testDrawCard() {
        deck.addCard(skipCard);
        deck.addCard(attackCard);
        int initialSize = deck.size();
        Card drawnCard = deck.removeTopCard();
        assertEquals(initialSize - 1, deck.size());
        assertTrue(drawnCard.equals(skipCard) || drawnCard.equals(attackCard));
    }

    @Test
    void testDrawFromEmptyDeck() {
        //Todo
    }

     @Test
    void testShuffleKeepsTheSameCards() {
        deck.addCard(skipCard);
        deck.addCard(attackCard);
        deck.shuffle();
        assertTrue(deck.getCards().contains(skipCard));
        assertTrue(deck.getCards().contains(attackCard));
    }
*/
}
