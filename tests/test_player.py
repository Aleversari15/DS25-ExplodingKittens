import pytest

from exploding_kittens.game.model.player import Player
from exploding_kittens.game.model.card import Card, CardType

EMPTY_HAND = 0
CARD_DRAWN = 1
CARD_DISCRAD = 1
HAND_SIZE = 3

@pytest.fixture
def player():
    return Player(1, "TestPlayer")

@pytest.fixture
def attack_card():
    return Card(CardType.ATTACK, "Attack", "")

@pytest.fixture
def skip_card():
    return Card(CardType.SKIP, "Skip", "")

@pytest.fixture
def favor_card():
    return Card(CardType.FAVOR, "Favor", "")

@pytest.fixture
def shuffle_card():
    return Card(CardType.SHUFFLE, "Shuffle", "")

@pytest.fixture
def see_future_card():
    return Card(CardType.SEE_THE_FUTURE, "See the future", "")



#Test that a player can be created with correct attributes
def test_player_creation(player):
    assert player.name == "TestPlayer"
    assert player.id == 1
    assert player.hand == []
    assert player.is_alive == True


#Test adding a card to player's hand
def test_player_add_card_to_hand(player, attack_card):
    player.add_card(attack_card)
    assert len(player.hand) == 1
    assert attack_card in player.hand

#Test adding multiple cards to player's hand
def test_player_add_multiple_cards(player, skip_card, favor_card, shuffle_card):
    player.add_card(skip_card)
    player.add_card(favor_card)
    player.add_card(shuffle_card)
    
    assert len(player.hand) == 3
    assert skip_card in player.hand
    assert favor_card in player.hand
    assert shuffle_card in player.hand


#Test removing a card from player's hand
def test_player_remove_card_from_hand(player, see_future_card):
    player.add_card(see_future_card)
    removed_card = player.remove_card(CardType.SEE_THE_FUTURE)  
    
    assert removed_card.type == CardType.SEE_THE_FUTURE  
    assert len(player.hand) == EMPTY_HAND

#Test hand_size returns correct count
def test_player_hand_size(player, skip_card, attack_card):
    assert player.hand_size() == EMPTY_HAND
    
    player.add_card(skip_card)
    assert player.hand_size() == CARD_DRAWN 
    
    hand = player.hand_size()
    player.add_card(attack_card)
    assert player.hand_size() == hand + CARD_DRAWN
    
    hand = player.hand_size()
    player.remove_card(CardType.SKIP)
    assert player.hand_size() == hand - CARD_DISCRAD

#Test multiple operations on same player
def test_player_multiple_operations(player, skip_card, attack_card, favor_card):
    # Add cards
    player.add_card(skip_card)
    player.add_card(attack_card)
    player.add_card(favor_card)
    
    assert player.hand_size() == HAND_SIZE    
    assert player.has_card(CardType.SKIP) == True
    
    # Remove a card
    player.remove_card(CardType.ATTACK)
    assert player.hand_size() == HAND_SIZE - CARD_DISCRAD
    assert player.has_card(CardType.ATTACK) == False
    
    # Player still alive
    assert player.is_alive == True
    
    # Eliminate
    player.eliminate()
    assert player.is_alive == False
    assert player.hand_size() == EMPTY_HAND