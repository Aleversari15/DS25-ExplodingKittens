import pygame
from typing import Optional
from ..game_state import GameState
from ..game_engine import GameEngine
from ..card import Card, CardType
from .colors import *

#Finestra principale del gioco
class GameWindow:
    
    #Inizializza la finestra di gioco 
    def __init__(self, width: int = 1200, height: int = 800):
        ...
    
    #Inizia una nuova partita con i giocatori specificati
    def start_game(self, player_names: list[str]):
        ...
    
    #Main loop del gioco
    def run(self):
       ...
    
    #Gestisci gli eventi, aggiorna lo stato e ridisegna la finestra ad ogni frame
    def _handle_events(self):
        ...
    
    #Gestisce i click del mouse sulla finestra
    def _handle_click(self, pos: tuple[int, int]):
        ...
    
    #Gestisce la pressione dei tasti sulla tastiera
    def _handle_keypress(self, key: int):
        ...
    
    #Aggiorna lo stato del gioco in base all'azione del giocatore e alle regole del gioco
    def _update(self):
        ...
    
    #Ridisegna la finestra con lo stato attuale del gioco
    def _draw(self):
        ...
    
    #FUNZIONI DI DISEGNO PER LE VARIE COMPONENTI DELLA VIEW
    
    def _draw_start_screen(self):
        ...
    
    def _draw_game_screen(self):
        ...
    
    def _draw_current_player_info(self):
        ...
    
    def _draw_deck(self):
        ...
    
    def _draw_discard_pile(self):
        ...
    
    def _draw_player_hand(self):
        ...
    
    #Disegna una singola carta alle coordinate specificate
    def _draw_card(self, card: Card, x: int, y: int, width: int = 100, height: int = 140):
        ...
    #Mostra le informazioni sugli altri giocatori (nome, numero carte)
    def _draw_other_players(self):
        ...
    
    #Ritorna il colore RGB associato a un tipo di carta 
    def _get_card_color(self, card_type: CardType) -> tuple:
        ...
    
    #Divide il testo in più righe per farlo stare nella larghezza specificata
    def _wrap_text(self, text: str, max_width: int) -> list[str]:
        ...