import pytest
from exploding_kittens.deck import Deck
from exploding_kittens.card import Card, CardType
from tests.test_game_state import deck


NUM_PLAYERS = 4
DECK_SIZE = 56
CARD_DRAWN = 1

class TestDeck:
#Test that building a standard deck creates the correct number of cards
    def test_build_standard_deck(self):
        deck = Deck.build_standard(NUM_PLAYERS)
        assert len(deck) == DECK_SIZE, f"Standard deck should have {DECK_SIZE} cards for {NUM_PLAYERS} players"
    
#Test that shuffling the deck changes the order of cards
    def test_shuffle_deck(self):    
        deck1 = Deck.build_standard(NUM_PLAYERS)
        deck2 = Deck.build_standard(NUM_PLAYERS)
        deck1.shuffle()
        assert any(c1 != c2 for c1, c2 in zip(deck1._cards, deck2._cards)), "Shuffling should change the order of cards"

#Test that drawing from the deck returns a Card and reduces the deck size
    def test_draw_card(self):
        deck = Deck.build_standard(NUM_PLAYERS)
        initial_length = len(deck)
        card = deck.draw()
        assert isinstance(card, Card), "Drawn object should be a Card"
        assert len(deck) == initial_length - CARD_DRAWN, "Deck should have one less card after drawing"
    
#Test that drawing from an empty deck raises EmptyDeckError
    def test_draw_from_empty_deck(self):
        deck = Deck()
        with pytest.raises(Exception) as exc_info:
            deck.draw()
        assert "vuoto" in str(exc_info.value), "Drawing from empty deck should raise EmptyDeckError"