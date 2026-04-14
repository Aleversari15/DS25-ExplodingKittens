package exploding_kittens.game.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Questa classe appresenta il mazzo di carte da gioco.
 */
public class Deck {
    private final List<Card> cards;

    /**
     * Costruisce un mazzo vuoto.
     */
    public Deck() {
        this.cards = new ArrayList<>();
    }

    /**
     * Verifica se il mazzo è vuoto.
     * @return true se il mazzo è vuoto, false altrimenti.
     */
    public boolean isEmpty() {
        return cards.isEmpty();
    }

    /**
     * Rimuove la carta in cima al mazzo.
     * @return la carta rimossa o null se il mazzo è vuoto.
     */
    public Card removeTopCard() {
        if (cards.isEmpty()) {
            return null;
        }
        return cards.removeFirst();
    }


    /**
     * Inserisce una carta nella posizione desiderata.
     * @param card carta da inserire nel mazzo
     * @param position posizione in cui verrà inserita la carta
     */
    public void insertCard(Card card, int position) {
        cards.add(position, card);
    }

    /**
     * Restituisce le prime n carte del mazzo senza rimuoverle.
     * @param n carte da restituire.
     * @return la lista di carte di lunghezza n in cima al mazzo.
     */
    public List<Card> peekTop(int n) {
        return new ArrayList<>(cards.subList(0, n));
    }

    /**
     * Restituisce il numero di carte presenti nel mazzo.
     * @return numero di carte presenti nel mazzo
     */
    public int size() {
        return cards.size();
    }

    /**
     * Restituisce una copia della lista di carte del mazzo.
     * @return lista delle carte.
     */
    public List<Card> getCards() {
        return new ArrayList<>(cards);
    }

    /**
     * Sostituisce completamente il contenuto del mazzo.
     * @param newCards che sostituiranno il contenuto del mazzo.
     */
    public void setCards(List<Card> newCards) {
        cards.clear();
        cards.addAll(newCards);
    }

    /**
     * Rimuove la prima carta trovata del tipo passato in input.
     * @param type della carta da rimuovere
     * @return carta rimossa
     */
    public Card removeCardOfType(CardType type) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).getType() == type) {
                return cards.remove(i);
            }
        }
        return null;
    }
}