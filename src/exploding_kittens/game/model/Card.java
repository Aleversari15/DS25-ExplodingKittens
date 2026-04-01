package exploding_kittens.game.model;

public class Card {
    private final CardType type;
    private final String name;
    private final String description;

    public Card(CardType type, String name, String description) {
        this.type = type;
        this.name = name;
        this.description = description;
    }

    public Card(CardType type) {
        this.type = type;
        this.name = type.name(); // Usa il nome dell'enum come nome carta
        this.description = "Carta di tipo " + type.name(); // Descrizione generica
    }

    public CardType getType() {
        return type;
    }
    public String getName() {
        return name;
    }
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name;
    }
}