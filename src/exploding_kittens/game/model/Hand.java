package exploding_kittens.game.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Rappresenta la mano di un giocatore.
 * Contiene l'insieme delle carte possedute dal giocatore e
 * fornisce metodi per aggiungere, rimuovere e cercare carte
 * in base al loro tipo.
 */
public class Hand {
    private final List<Card> cards;

    /**
     * Costruttore di default che inizializza una mano vuota.
     */
    public Hand() {
        this.cards = new ArrayList<>();
    }

    /**
     * Aggiunge una carta alla mano del giocatore.
     * @param card da aggiugere.
     */
    public void addCard(Card card) { cards.add(card); }

    /**
     * Rimuove una carta dalla mano del giocatore.
     * @param card da rimuovere.
     */
    public void removeCard(Card card) {
        cards.remove(card);
    }

    /**
     * Verifica se la mano contiene almeno una carta di un certo tipo.
     * @param type il tipo di carta da cercare
     * @return true se presente, false altrimenti.
     */
    public boolean hasCardOfType(CardType type) {
        return cards.stream().anyMatch(c -> c.getType() == type);
    }

    /**
     * Restituisce la prima carta trovata di un determinato tipo.
     * @param type il tipo di carta da cercare.
     * @return la carta trovata oppure null se non presente.
     */
    public Card getCardOfType(CardType type) {
        return cards.stream()
                .filter(c -> c.getType() == type)
                .findFirst()
                .orElse(null);
    }

    /**
     * Restituisce tutte le carte presenti nella mano.
     * @return lista delle carte.
     */
    public List<Card> getCards() { return new ArrayList<>(cards); }

    /**
     * Restituisce il numero di carte nella mano.
     * @return dimensione della mano.
     */
    public int size() { return cards.size(); }
}