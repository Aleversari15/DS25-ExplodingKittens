from enum import Enum, auto
from dataclasses import dataclass

class CardType(Enum):
    EXPLODING_KITTEN = auto()
    DEFUSE = auto()
    FAVOR = auto()
    SKIP = auto()
    ATTACK = auto()
    SHUFFLE = auto()
    SEE_THE_FUTURE = auto()
    NOPE = auto()
    CAT = auto() 

@dataclass(frozen=True)
class Card:
    type: CardType
    name: str
    description: str

    #Ritorna True se la carta può essere giocata attivamente (non Exploding/Defuse).
    def is_playable(self) -> bool:
        return self.type not in {CardType.EXPLODING_KITTEN, CardType.DEFUSE}

    #Ritorna True se l'effetto di questa carta può essere annullato da un Nope.
    def can_be_noped(self) -> bool:
        return self.type not in {CardType.EXPLODING_KITTEN, CardType.DEFUSE, CardType.CAT}