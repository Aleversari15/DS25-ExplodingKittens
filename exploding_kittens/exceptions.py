#Base exception per tutto il progetto.
class ExplodingKittensError(Exception): ...

class EmptyDeckError(ExplodingKittensError):
    """Deck vuoto quando si tenta di pescare."""

class CardNotInHandError(ExplodingKittensError):
    """Il giocatore non ha la carta che vuole giocare."""

class InvalidMoveError(ExplodingKittensError):
    """Mossa non valida secondo le regole."""
    def __init__(self, reason: str):
        super().__init__(reason)
        self.reason = reason

class PlayerNotFoundError(ExplodingKittensError):
    """ID giocatore non riconosciuto."""