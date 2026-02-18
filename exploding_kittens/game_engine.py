
import random
from typing import Optional
from . import exceptions
from .game_state import GameState
from .card import Card, CardType
from .player import Player
from .deck import Deck


INITIAL_HAND_SIZE = 7


class GameEngine:

    #Crea e ritorna uno stato iniziale valido:
    #    - Costruisce il deck standard
    #   - Distribuisce 7 carte (INITIAL_HAND_SIZE) + 1 Defuse a ogni giocatore
    #    - Aggiunge le Exploding Kittens e mischia
    @staticmethod
    def setup_game(player_names: list[str]) -> GameState:
        players = [Player(id=str(i), name=name) for i, name in enumerate(player_names)]
        deck = Deck.build_standard(len(player_names))
        for player in players:
            for _ in range(INITIAL_HAND_SIZE):
                player.add_card(deck.draw())
            player.add_card(Card(CardType.DEFUSE, "Defuse", "Usata per disinnescare una Exploding Kitten"))
        deck.add_exploding_kittens(len(player_names))
        deck.shuffle()
        
        return GameState(players,deck)       


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
    def draw_card(state: GameState) -> GameState:
        player = state.current_player
        try:
            card = state.deck.draw()
        except exceptions.EmptyDeckError:
            #se il mazzo è vuoto, ricicla le carte scartate e mischia
            state.deck._cards = state.discard_pile.copy()
            state.discard_pile.clear()
            state.deck.shuffle()
            card = state.deck.draw() 
            
        if card.type == CardType.EXPLODING_KITTEN:
            if player.has_card(CardType.DEFUSE):
                #inserisce in posizione casuale nel mazzo
                return GameEngine.use_defuse(state, random.randint(0, len(state.deck._cards)))  
            else:
                player.eliminate()
        else:
            player.add_card(card)  
        return state
        

    #Il giocatore usa un Defuse per rimettere la Exploding Kitten nel deck
    #alla posizione scelta.
    @staticmethod
    def use_defuse(state: GameState, insert_position: int) -> GameState:
        player = state.current_player
        player.remove_card(CardType.DEFUSE)
        state.deck.insert(Card(CardType.EXPLODING_KITTEN, "Exploding Kitten", "Pesca questa carta e perdi se non hai un Defuse!"), insert_position)
        return state



    #EFFETTI DELLE SINGOLE CARTE 
    @staticmethod
    def _apply_skip(state: GameState) -> GameState: ...

    @staticmethod
    def _apply_attack(state: GameState) -> GameState: ...

    @staticmethod
    def _apply_shuffle(state: GameState) -> GameState: 
        state.deck.shuffle()
        return state

    @staticmethod
    def _apply_see_the_future(state: GameState, player_id: str) -> GameState: ...