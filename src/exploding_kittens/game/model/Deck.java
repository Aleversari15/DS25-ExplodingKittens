package exploding_kittens.game.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {
    private final List<Card> cards;

    public Deck() {

        this.cards = new ArrayList<>();
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    public void addCard(Card card) {

        cards.add(card);
    }
    public Card removeTopCard() {
        return cards.removeFirst();
    }
    public Card getCard(int index) {

        return cards.get(index);
    }
    public void insertCard(Card card, int position) {

        cards.add(position, card);
    }
    public List<Card> peekTop(int n) {

        return new ArrayList<>(cards.subList(0, n));
    }
    public int size() {

        return cards.size();
    }
    public List<Card> getCards() {
        return new ArrayList<>(cards);
    }
    public void setCards(List<Card> newCards) {
        cards.clear();
        cards.addAll(newCards);
    }
}