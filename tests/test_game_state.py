import pytest
from exploding_kittens.game_state import GameState
from exploding_kittens.player import Player
from exploding_kittens.deck import Deck
from exploding_kittens.card import Card, CardType

@pytest.fixture
def players():
    return [
        Player(id=1, name="Player1"),
        Player(id=2, name="Player2"),
        Player(id=3, name="Player3"),
    ]


@pytest.fixture
def deck():
    return Deck()


@pytest.fixture
def game_state(players, deck):
    return GameState(players=players, deck=deck)


class TestGameStateInitialization:
    def test_game_state_initialization(self, game_state):
        assert len(game_state.players) == 3
        assert game_state.current_player_index == 0
        assert game_state.turns_to_play == 1
        assert game_state.direction == 1
        assert game_state.winner is None
        assert game_state.pending_nope is False
        assert len(game_state.discard_pile) == 0


class TestCurrentPlayer:
    def test_current_player_returns_first_player(self, game_state):
        assert game_state.current_player == game_state.players[0]
        assert game_state.current_player.name == "Player1"

    def test_current_player_after_index_change(self, game_state):
        game_state.current_player_index = 1
        assert game_state.current_player == game_state.players[1]
        assert game_state.current_player.name == "Player2"


class TestAlivePlayers:
    def test_all_players_alive_initially(self, game_state):
        assert len(game_state.alive_players) == 3
        assert all(p.is_alive for p in game_state.alive_players)

    def test_alive_players_excludes_dead_player(self, game_state):
        game_state.players[1].is_alive = False
        alive = game_state.alive_players
        assert len(alive) == 2
        assert game_state.players[1] not in alive


class TestIsGameOver:
    def test_game_not_over_with_multiple_players(self, game_state):
        assert not game_state.is_game_over

    def test_game_over_with_one_alive_player(self, game_state):
        game_state.players[0].is_alive = False
        game_state.players[1].is_alive = False
        assert game_state.is_game_over

    def test_game_not_over_with_two_alive_players(self, game_state):
        game_state.players[0].is_alive = False
        assert not game_state.is_game_over


class TestNextPlayer:
    def test_next_player_clockwise(self, game_state):
        game_state.next_player()
        assert game_state.current_player_index == 1

    def test_next_player_skips_dead_players(self, game_state):
        game_state.players[1].is_alive = False
        game_state.next_player()
        assert game_state.current_player_index == 2
        assert game_state.current_player.name == "Player3"

    def test_next_player_counter_clockwise(self, game_state):
        game_state.direction = -1
        game_state.current_player_index = 2
        game_state.next_player()
        assert game_state.current_player_index == 1

    def test_next_player_sets_winner_when_game_over(self, game_state):
        game_state.players[0].is_alive = False
        game_state.players[1].is_alive = False
        game_state.next_player()
        assert game_state.winner == game_state.players[2]

    def test_next_player_skips_multiple_dead_players(self, game_state):
        game_state.players.append(Player(id=4, name="Player4"))
        game_state.players[1].is_alive = False
        game_state.players[2].is_alive = False
        game_state.next_player()
        assert game_state.current_player_index == 3


class TestDiscardPile:
    def test_discard_pile_starts_empty(self, game_state):
        assert len(game_state.discard_pile) == 0

    def test_can_add_cards_to_discard_pile(self, game_state):
        card = Card(CardType.DEFUSE, "Defuse", "Usata per disinnescare una Exploding Kitten")
        game_state.discard_pile.append(card)
        assert len(game_state.discard_pile) == 1
        assert game_state.discard_pile[0] == card


class TestTurnsAndDirection:
    def test_default_turns_to_play(self, game_state):
        assert game_state.turns_to_play == 1

    def test_default_direction_is_clockwise(self, game_state):
        assert game_state.direction == 1

    def test_can_change_direction_to_counter_clockwise(self, game_state):
        game_state.direction = -1
        assert game_state.direction == -1

    def test_can_increase_turns_to_play(self, game_state):
        game_state.turns_to_play = 2
        assert game_state.turns_to_play == 2


