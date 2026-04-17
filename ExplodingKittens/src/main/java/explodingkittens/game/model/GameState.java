package explodingkittens.game.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Questa classe rappresenta lo stato corrente della partita.
 * Mantiene informazioni sui giocatori attivi, sul turno corrente e sulla gestione dei turni multipli
 * (ad esempio dovuti alla carta Attack).
 */
public class GameState {
    private final List<Player> activePlayers;
    private int currentPlayerIndex;
    private int turnsToPlay;

    /**
     * Costruttore di default.
     * Inizializza la lista dei giocatori e imposta il primo turno.
     */
    public GameState() {
        this.activePlayers = new ArrayList<>();
        this.currentPlayerIndex = 0;
        this.turnsToPlay = 1;
    }

    /**
     * Aggiunge un nuovo giocatore alla partita.
     * @param player il giocatore da aggiungere
     */
    public void addPlayer(Player player) {
        activePlayers.add(player);
    }

    /**
     * Rimuove un giocatore dalla partita.
     * Aggiorna correttamente l'indice del giocatore corrente:
     * - Se il giocatore ha già giocato, ma il giro non è terminato, l'indice del turno viene decrementato.
     * - Se l'indice del giocatore di turno è >= del numero di giocatori attivi e la lista dei giocatori attivi non è
     *   vuota, l'indice del turno viene resettato a 0
     * @param player il giocatore da rimuovere.
     */
    public void removePlayer(Player player) {
        int indexToRemove = -1;
        for (int i = 0; i < activePlayers.size(); i++) {
            if (activePlayers.get(i).getAgentName().equals(player.getAgentName())) {
                indexToRemove = i;
                break;
            }
        }
        activePlayers.remove(player);
        if (indexToRemove < currentPlayerIndex) {
            currentPlayerIndex--;
        }
        if (currentPlayerIndex >= activePlayers.size() && !activePlayers.isEmpty()) {
            currentPlayerIndex = 0;
        }
    }

    /**
     * Restituisce l'indice del giocatore corrente.
     * @return indice del giocatore corrente
     */
    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }

    /**
     * Restituisce il giocatore corrente.
     * @return il giocatore attivo nel turno corrente
     */
    public Player getCurrentPlayer() {
        return activePlayers.get(currentPlayerIndex);
    }

    /**
     * Restituisce la lista dei giocatori attivi.
     * @return lista dei giocatori attivi.
     */
    public List<Player> getActivePlayers() {
        return new ArrayList<>(activePlayers);
    }

    /**
     * Passa al turno successivo tenendo conto del numero di turni residui (es. carta Attack).
     */
    public void nextTurn() {
        turnsToPlay--;
        if (turnsToPlay <= 0) {
            currentPlayerIndex = (currentPlayerIndex + 1) % activePlayers.size();
            turnsToPlay = 1;
        }
    }

    /**
     * Imposta il numero di turni che il giocatore corrente deve giocare.
     * @param n numero di turni
     */
    public void setTurnsToPlay(int n) {
        this.turnsToPlay = n;
    }

    /**
     *  Restituisce il numero di turni rimanenti per il giocatore corrente.
     * @return turni rimanenti
     */
    public int getTurnsToPlay() {
        return turnsToPlay;
    }

    /**
     * Verifica se la partita è terminata.
     * @return true se è rimasto solo un giocatore in vita, false altrimenti.
     */
    public boolean isGameOver() {
        return activePlayers.size() == 1;
    }

    /**
     * Restituisce il vincitore della partita.
     * @return unico player rimasto in vita.
     */
    public Player getWinner() {
        return isGameOver() ? activePlayers.getFirst() : null;
    }

    /**
     * Imposta manualmente l'indice del giocatore corrente.
     * @param index nuovo indice.
     */
    public void setCurrentPlayerIndex(int index) {
        if (!activePlayers.isEmpty()) {
            this.currentPlayerIndex = index % activePlayers.size();
        }
    }

    /**
     * Sostituisce completamente la lista dei giocatori attivi.
     * @param players nuova lista di giocatori.
     */
    public void setActivePlayers(List<Player> players) {
        this.activePlayers.clear();
        this.activePlayers.addAll(players);
    }
}
