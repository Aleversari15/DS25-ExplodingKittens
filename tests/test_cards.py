import pytest
from exploding_kittens.card import Card, CardType



#Test that playable card types return True
def test_card_is_playable_for_playable_types():

    playable_types = [
        CardType.ATTACK,
        CardType.SKIP,
        CardType.FAVOR,
        CardType.SHUFFLE,
        CardType.SEE_THE_FUTURE,
        CardType.NOPE
    ]
    
    for card_type in playable_types:
        card = Card(card_type, f"{card_type.name} Card", "Test description")
        assert card.is_playable() == True, f"{card_type.name} should be playable"

#Test that non-playable card types return False
def test_card_is_not_playable_for_non_playable_types():

    non_playable_types = [
        CardType.EXPLODING_KITTEN,
        CardType.DEFUSE
    ]
    
    for card_type in non_playable_types:
        card = Card(card_type, f"{card_type.name} Card", "Test description")
        assert card.is_playable() == False, f"{card_type.name} should not be playable"


