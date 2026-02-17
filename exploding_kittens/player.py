from dataclasses import dataclass, field
from .card import Card, CardType

@dataclass
class Player:
    id: str
    name: str
    hand: list[Card] = field(default_factory=list)
    is_alive: bool = True

    #Aggiunge una carta alla mano del giocatore.
    def add_card(self, card: Card) -> None:
        ...

    #Rimuove e ritorna una carta dalla mano. Solleva CardNotInHandError se assente.
    def remove_card(self, card_type: CardType) -> Card:
        ...

    #Controlla se il giocatore ha almeno una carta di quel tipo.
    def has_card(self, card_type: CardType) -> bool:
        ...

    #Restituisce il numero di carte nella mano del giocatore.
    def hand_size(self) -> int: ...

    #Segna il giocatore come eliminato e svuota la mano.
    def eliminate(self) -> None:
        ...