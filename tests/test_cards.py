import pytest
from exploding_kittens.card import Card, CardType


class TestCard:
#Test that playable card types return True
    def test_card_is_playable_for_playable_types(self):

        playable_types = [
            CardType.ATTACK,
            CardType.SKIP,
            CardType.FAVOR,
            CardType.SHUFFLE,
            CardType.SEE_THE_FUTURE,
        ]
    
        for card_type in playable_types:
            card = Card(card_type, f"{card_type.name} Card", "Test description")
            assert card.is_playable() == True, f"{card_type.name} should be playable"


