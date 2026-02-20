import pytest
from exploding_kittens.game_engine import GameEngine
from exploding_kittens.game_state import GameState
from exploding_kittens.player import Player
from exploding_kittens.deck import Deck
from exploding_kittens.card import Card, CardType
from exploding_kittens.exceptions import EmptyDeckError

EMPTY_SIZE = 0
DISCARD_PILE_SIZE = 3
CARD_DRAWN = 1
HAND_SIZE = 0

@pytest.fixture
def player():
    return Player("1", name="Player1")

#Fixture per un deck vuoto
@pytest.fixture
def empty_deck():
    return Deck(_cards=[])

#Fixture per uno stato di gioco con deck vuoto e carte nella discard pile
@pytest.fixture
def game_state_with_empty_deck(player, empty_deck):
    discard_pile = [
        Card(CardType.SKIP, "Skip", ""),
        Card(CardType.ATTACK, "Attack", ""),
        Card(CardType.SHUFFLE, "Shuffle", "")
    ]
    return GameState(
        players=[player],
        deck=empty_deck,
        discard_pile=discard_pile
    )

#Test per verificare il comportamento del draw_card quando il deck è vuoto e deve riciclare la discard pile
class TestDeckRecycling:
    
    #Verifica che un deck vuoto sollevi EmptyDeckError prima di tentare il riciclo
    def test_empty_deck_raises_exception_before_recycling(self, empty_deck):
        with pytest.raises(EmptyDeckError):
            empty_deck.draw()
    
    #Verifica che quando si pesca da un deck vuoto, la discard pile venga riciclata e mischiata
    def test_draw_card_recycles_discard_pile_when_deck_empty(self, game_state_with_empty_deck):
   
        # Situazione iniziale
        assert len(game_state_with_empty_deck.deck) == EMPTY_SIZE
        assert len(game_state_with_empty_deck.discard_pile) == DISCARD_PILE_SIZE
        
        # Salva le carte della discard pile per verificare dopo
        discard_cards = game_state_with_empty_deck.discard_pile.copy()
       
        new_state = GameEngine.draw_card(game_state_with_empty_deck)
        assert len(new_state.discard_pile) == EMPTY_SIZE, "Discard pile should be empty after recycling"
        
        # Verifica che il deck contenga le carte riciclate (meno quella pescata)
        assert len(new_state.deck) == DISCARD_PILE_SIZE - CARD_DRAWN, "Deck should contain the recycled cards minus the one drawn"
        assert len(new_state.players[0].hand) == HAND_SIZE + CARD_DRAWN
    
    #Verifica che si possa pescare più di una carta dal deck riciclato finché non è vuoto
    def test_multiple_draws_from_recycled_deck(self, game_state_with_empty_deck):
        state = game_state_with_empty_deck
        
        for i in range(1, DISCARD_PILE_SIZE):
            state = GameEngine.draw_card(state)
            assert len(state.players[0].hand) == HAND_SIZE + i*CARD_DRAWN
            assert len(state.deck) == DISCARD_PILE_SIZE - i*CARD_DRAWN
        

    
    #Verifica che se si pesca un Exploding Kitten dal deck riciclato, il giocatore possa usare un Defuse
    def test_draw_exploding_kitten_after_recycling(self, player, empty_deck):
      
        player.add_card(Card(CardType.DEFUSE, "Defuse", ""))
        discard_pile = [
            Card(CardType.EXPLODING_KITTEN, "Exploding Kitten", "Boom!"),
            Card(CardType.SKIP, "Skip", "")
        ]
        
        state = GameState(
            players=[player],
            deck=empty_deck,
            discard_pile=discard_pile
        )
        
        # Prima pescata - ricicla il deck
        new_state = GameEngine.draw_card(state)
        
        # Se ha pescato l'Exploding Kitten, dovrebbe aver usato il Defuse
        # Il giocatore dovrebbe essere ancora vivo
        assert new_state.players[0].is_alive == True
        
    #Verifica che dopo il riciclo, la discard pile sia vuota
    def test_discard_pile_empty_after_recycling(self, game_state_with_empty_deck):
    
        initial_discard_size = len(game_state_with_empty_deck.discard_pile)
        assert initial_discard_size > EMPTY_SIZE
        
        new_state = GameEngine.draw_card(game_state_with_empty_deck)
        
        assert len(new_state.discard_pile) == EMPTY_SIZE
    
