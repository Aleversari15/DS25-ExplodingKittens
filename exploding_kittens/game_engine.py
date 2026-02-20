
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
        player = state.get_player(player_id)
        
        if state.current_player.id != player_id:
            raise exceptions.InvalidMoveError(f"Non è il turno di {player_id}")
        
        if not player.has_card(card_type):
            raise exceptions.CardNotInHandError(f"Il giocatore non ha una carta {card_type.name}")
        
        cardPlayed  = player.remove_card(card_type)
        state.discard_pile.append(cardPlayed)

        if cardPlayed.can_be_noped():
            state.pending_nope = True

        if card_type == CardType.SKIP:
            state = GameEngine._apply_skip(state)
        elif card_type == CardType.ATTACK:
            state = GameEngine._apply_attack(state)
        elif card_type == CardType.SHUFFLE:
            state = GameEngine._apply_shuffle(state)
        elif card_type == CardType.SEE_THE_FUTURE:
            state = GameEngine._apply_see_the_future(state, player_id)
    
        return state    
        
        

    
    #Il giocatore pesca una carta. Se è una Exploding Kitten,
    #controlla se ha un Defuse, altrimenti viene eliminato.
    @staticmethod
    def draw_card(state: GameState) -> GameState:
        player = state.current_player
        try:
            card = state.deck.draw()
        except exceptions.EmptyDeckError:
            # se il mazzo è vuoto, ricicla le carte scartate e mischia
            state.deck._cards = state.discard_pile.copy()
            state.discard_pile.clear()
            state.deck.shuffle()
            card = state.deck.draw() 
            
        if card.type == CardType.EXPLODING_KITTEN:
            if player.has_card(CardType.DEFUSE):
                # La bomba è già stata pescata, quindi la rimettiamo
                # Posizione casuale nel range del deck attuale (0 a lunghezza deck)
                insert_pos = random.randint(0, len(state.deck))
                state = GameEngine.use_defuse(state, insert_pos)
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



    #----------------EFFETTI DELLE SINGOLE CARTE ----------------------#
    @staticmethod
    def _apply_skip(state: GameState) -> GameState: 
        state.next_player()
        return state

    #La carta attack obbliga il giocatore successivo a giocare due turni 
    #quindi aumento il numero di tuno e passo al giocatore successivo
    @staticmethod
    def _apply_attack(state: GameState) -> GameState: 
        state.turns_to_play+=1
        state.next_player()
        return state


    @staticmethod
    def _apply_shuffle(state: GameState) -> GameState: 
        state.deck.shuffle()
        return state

    @staticmethod
    def _apply_see_the_future(state: GameState, player_id: str) -> GameState:
        future_cards = state.deck.peek(3)
        state.private_info = {
            "type": "see_the_future",
            "player_id": player_id,
            "cards": future_cards
        }
        return state
    