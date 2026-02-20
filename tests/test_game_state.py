import pytest
from exploding_kittens.game_state import GameState
from exploding_kittens.player import Player
from exploding_kittens.deck import Deck
from exploding_kittens.card import Card, CardType

PLAYER_ID = 1
PLAYER_ID_2 = 2
PLAYER_ID_3 = 3
PLAYER_ID_4 = 4

PLAYER_INDEX = 0
PLAYER_INDEX_2 = 1
PLAYER_INDEX_3 = 2
PLAYER_INDEX_4 = 3

GAME_DIRECTION_CLOCKWISE = 1

EMPTY_SIZE = 0
PALYERS_SIZE = 3

PLAYER_NAME = "Player1"
PLAYER_NAME_2 = "Player2"
PLAYER_NAME_3 = "Player3"
PLAYER_NAME_4 = "Player4"

CARD_DISCARD = 1
FIRST_CARD_DISCRD = 0

@pytest.fixture
def players():
    return [
        Player(PLAYER_ID, name=PLAYER_NAME),
        Player(PLAYER_ID_2, name=PLAYER_NAME_2),
        Player(PLAYER_ID_3, name=PLAYER_NAME_3),
    ]


@pytest.fixture
def deck():
    return Deck()


@pytest.fixture
def game_state(players, deck):
    return GameState(players=players, deck=deck)


class TestGameStateInitialization:
    def test_game_state_initialization(self, game_state):
        assert len(game_state.players) == PALYERS_SIZE
        assert game_state.players[PLAYER_INDEX].id == PLAYER_ID
        assert game_state.current_player_index == PLAYER_INDEX
        assert game_state.turns_to_play == PLAYER_ID
        assert game_state.direction == GAME_DIRECTION_CLOCKWISE
        assert game_state.winner is None
        assert len(game_state.discard_pile) == EMPTY_SIZE


class TestCurrentPlayer:
    def test_current_player_returns_first_player(self, game_state):
        assert game_state.current_player == game_state.players[PLAYER_INDEX]
        assert game_state.current_player.name == PLAYER_NAME

    def test_current_player_after_index_change(self, game_state):
        game_state.current_player_index = PLAYER_INDEX_2
        assert game_state.current_player == game_state.players[PLAYER_INDEX_2]
        assert game_state.current_player.name == PLAYER_NAME_2


class TestAlivePlayers:
    def test_all_players_alive_initially(self, game_state):
        assert len(game_state.alive_players) == PALYERS_SIZE
        assert all(p.is_alive for p in game_state.alive_players)

    def test_alive_players_excludes_dead_player(self, game_state):
        game_state.players[PLAYER_INDEX_2].is_alive = False
        alive = game_state.alive_players
        assert len(alive) == PALYERS_SIZE - 1
        assert game_state.players[PLAYER_INDEX_2] not in alive


class TestIsGameOver:
    def test_game_not_over_with_multiple_players(self, game_state):
        assert not game_state.is_game_over

    def test_game_over_with_one_alive_player(self, game_state):
        game_state.players[PLAYER_INDEX].is_alive = False
        game_state.players[PLAYER_INDEX_2].is_alive = False
        assert game_state.is_game_over

    def test_game_not_over_with_two_alive_players(self, game_state):
        game_state.players[PLAYER_INDEX].is_alive = False
        assert not game_state.is_game_over


class TestNextPlayer:
    def test_next_player_clockwise(self, game_state):
        game_state.next_player()
        assert game_state.current_player_index == PLAYER_INDEX_2

    def test_next_player_skips_dead_players(self, game_state):
        game_state.players[PLAYER_INDEX_2].is_alive = False
        game_state.next_player()
        assert game_state.current_player_index == PLAYER_INDEX_3

    def test_next_player_sets_winner_when_game_over(self, game_state):
        game_state.players[PLAYER_INDEX].is_alive = False
        game_state.players[PLAYER_INDEX_2].is_alive = False
        game_state.next_player()
        assert game_state.winner == game_state.players[PLAYER_INDEX_3]



class TestDiscardPile:
    def test_discard_pile_starts_empty(self, game_state):
        assert len(game_state.discard_pile) == EMPTY_SIZE

    def test_can_add_cards_to_discard_pile(self, game_state):
        card = Card(CardType.DEFUSE, "Defuse", "Usata per disinnescare una Exploding Kitten")
        game_state.discard_pile.append(card)
        assert len(game_state.discard_pile) == CARD_DISCARD
        assert game_state.discard_pile[FIRST_CARD_DISCRD] == card




