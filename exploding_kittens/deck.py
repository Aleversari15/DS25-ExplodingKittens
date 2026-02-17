from dataclasses import dataclass, field
from typing import Optional
from .card import Card, CardType

@dataclass
class Deck:
    _cards: list[Card] = field(default_factory=list)

    #Costruisce il deck standard per una partita con num_players giocatori.
    #Esclude le Exploding Kittens e i Defuse iniziali (vengono distribuiti separatamente).
    @classmethod
    def build_standard(cls, num_players: int) -> "Deck":
        ...

    #Mischia le carte in modo casuale.
    def shuffle(self) -> None:
        ...

    #Pesca la carta in cima al deck. Solleva EmptyDeckError se vuoto.
    def draw(self) -> Card:
        ...


    #Inserisce una carta a una posizione specifica (usato da Defuse).
    #position=0 è in cima, position=-1 è in fondo.
    def insert(self, card: Card, position: int) -> None:
        ...

    #Ritorna le prime n carte senza rimuoverle (usato da SeeTheFuture).
    def peek(self, n: int) -> list[Card]:
        ...

    #Aggiunge le Exploding Kittens al deck (num_players - 1).
    def add_exploding_kittens(self, num_players: int) -> None:
        ...

    def __len__(self) -> int: ...
    
    def __contains__(self, card_type: CardType) -> bool: ...