from typing import Optional
from .game_state import GameState
from .card import Card, CardType
from .player import Player
from .deck import Deck

class GameEngine:

    #Crea e ritorna uno stato iniziale valido:
    #    - Costruisce il deck standard
    #   - Distribuisce 7 carte + 1 Defuse a ogni giocatore
    #    - Aggiunge le Exploding Kittens e mischia
    @staticmethod
    def setup_game(player_names: list[str]) -> GameState:
        ...

    #Il giocatore gioca una carta dalla sua mano.
    #Valida la mossa, applica l'effetto, ritorna il nuovo stato.
    #Lancia eccezione InvalidMoveError se la mossa non è legale.
    @staticmethod
    def play_card(state: GameState, player_id: str, card_type: CardType) -> GameState:
        ...

    #Un giocatore gioca un Nope sulla carta precedente.
    #Può essere chiamato da qualsiasi giocatore fuori turno.
    @staticmethod
    def play_nope(state: GameState, player_id: str) -> GameState:
        ...

    #Il giocatore pesca una carta. Se è una Exploding Kitten,
    #controlla se ha un Defuse, altrimenti viene eliminato.
    @staticmethod
    def draw_card(state: GameState, player_id: str) -> GameState:
        ...

    #Il giocatore usa un Defuse per rimettere la Exploding Kitten nel deck
    #alla posizione scelta.
    @staticmethod
    def use_defuse(state: GameState, player_id: str, insert_position: int) -> GameState:
        ...



    #EFFETTI DELLE SINGOLE CARTE 
    @staticmethod
    def _apply_skip(state: GameState) -> GameState: ...

    @staticmethod
    def _apply_attack(state: GameState) -> GameState: ...

    @staticmethod
    def _apply_shuffle(state: GameState) -> GameState: ...

    @staticmethod
    def _apply_see_the_future(state: GameState, player_id: str) -> GameState: ...