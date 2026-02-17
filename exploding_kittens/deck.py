from dataclasses import dataclass, field
import random
from typing import Optional
from .card import Card, CardType
from .exceptions import EmptyDeckError

@dataclass
class Deck:
    _cards: list[Card] = field(default_factory=list)

    #Costruisce il mazzo completo standard per una partita con num_players giocatori (56 carte).
    #Esclude le Exploding Kittens e i Defuse iniziali (vengono distribuiti separatamente).
    @classmethod
    def build_standard(cls, num_players: int) -> "Deck":
       cards = []
       cards += Card(CardType.ATTACK, "Attack", "Termina il turno del giocatore e obbliga il prossimo a fare due turni consecutivi") * 4
       cards += Card(CardType.SKIP, "Skip", "Termina immediatamente il turno senza pescare una carta") * 4
       cards += Card(CardType.SHUFFLE, "Shuffle", "Mischia il mazzo attuale, rendendo casuale il prossimo draw") * 4
       cards += Card(CardType.SEE_THE_FUTURE, "See the future", "Permette di guardare le prossime 3 carte del mazzo") * 5
       cards += Card(CardType.NOPE, "Nope", "Annulla l’azione di un altro giocatore (può bloccare quasi tutte le carte)") * 5
       cards += Card(CardType.CAT, "Cat", "Carte con illustrazioni di gatti; senza effetti speciali, ma usabili per coppie/combos") * 24

       deck = Deck(cards)
       deck.shuffle
       return deck


    #Mischia le carte in modo casuale.
    def shuffle(self) -> None:
        random.shuffle(self._cards)

    #Pesca la carta in cima al deck. Solleva EmptyDeckError se vuoto.
    def draw(self) -> Card:
        if len(self) == 0:
            raise exceptions.EmptyDeckError("Impossibile pescare, mazzo vuoto!")
        return self._cards.pop(0)

    #Inserisce una carta a una posizione specifica (usato da Defuse).
    #position=0 è in cima, position=-1 è in fondo.
    def insert(self, card: Card, position: int) -> None:
        self._cards.insert(position, card)

    #Ritorna le prime n carte senza rimuoverle (usato da SeeTheFuture).
    def peek(self, n: int) -> list[Card]:
        return self._cards[:n] 

    #Aggiunge le Exploding Kittens al deck (num_players - 1).
    def add_exploding_kittens(self, num_players: int) -> None:
        for _ in range(num_players - 1):
            self._cards.append(Card(CardType.EXPLODING_KITTEN))

    def __len__(self) -> int: 
        return len(self._cards)

    #Così possiamo usare tipoCarta in Deck per fare il controllo
    def __contains__(self, card_type: CardType) -> bool: 
        return any(card.type == card_type for card in self._cards)