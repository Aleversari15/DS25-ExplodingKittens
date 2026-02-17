from dataclasses import dataclass, field
from typing import Optional
from .player import Player
from .deck import Deck
from .card import Card, CardType

@dataclass
class GameState:
    players: list[Player]
    deck: Deck
    discard_pile: list[Card] = field(default_factory=list)
    current_player_index: int = 0
    turns_to_play: int = 1          # aumenta con Attack
    direction: int = 1              # 1 = orario, -1 = antiorario
    winner: Optional[Player] = None
    pending_nope: bool = False      # True se l'ultima carta giocata è in attesa di Nope

    #Il giocatore che deve agire in questo momento.
    @property
    def current_player(self) -> Player:
        return self.players[self.current_player_index]

    #Lista dei giocatori ancora in gioco.
    @property
    def alive_players(self) -> list[Player]:
        return [player for player in self.players if player.is_alive]

    #True se c'è solo un giocatore rimasto.
    @property
    def is_game_over(self) -> bool:
        return len(self.alive_players) == 1

    #Avanza al prossimo giocatore vivo, tenendo conto di turns_to_play e direction.
    def next_player(self) -> None:
        if self.is_game_over:
           self.winner = self.alive_players[0]
           return
        self.current_player_index = self.current_player_index + self.direction
        #salta i giocatori morti
        while not self.current_player.is_alive:
              self.current_player_index = self.current_player_index + self.direction

    #Serializza lo stato per inviarlo via rete (JSON-serializable).
    def to_dict(self) -> dict:
        ...

    #Ricostruisce lo stato da un dict ricevuto via rete.
    @classmethod
    def from_dict(cls, data: dict) -> "GameState":
        ...