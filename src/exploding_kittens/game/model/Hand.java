package exploding_kittens.game.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Hand {
    private final List<Card> cards;

    public Hand() {
        this.cards = new ArrayList<>();
    }

    public void addCard(Card card) { cards.add(card); }
    public boolean removeCard(Card card) { return cards.remove(card); }
    public boolean hasCardOfType(CardType type) {
        return cards.stream().anyMatch(c -> c.getType() == type);
    }
    public Card getCardOfType(CardType type) {
        return cards.stream()
                .filter(c -> c.getType() == type)
                .findFirst()
                .orElse(null);
    }
    public List<Card> getCardsOfType(CardType type) {
        return cards.stream()
                .filter(c -> c.getType() == type)
                .collect(Collectors.toList());
    }
    public List<Card> getCards() { return new ArrayList<>(cards); }
    public int size() { return cards.size(); }
}