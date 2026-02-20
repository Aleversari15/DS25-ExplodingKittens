#Base exception per tutto il progetto.
class ExplodingKittensError(Exception): 
    ...

#Deck vuoto quando si tenta di pescare.
class EmptyDeckError(ExplodingKittensError):
    
    def __init__(self, message: str):
        super().__init__(message)
        self.message = message
    
#Il giocatore non ha la carta che vuole giocare.
class CardNotInHandError(ExplodingKittensError):
    ...

#Mossa non valida secondo le regole.
class InvalidMoveError(ExplodingKittensError):
   
    def __init__(self, reason: str):
        super().__init__(reason)
        self.reason = reason

#ID giocatore non riconosciuto.
class PlayerNotFoundError(ExplodingKittensError):
  ...