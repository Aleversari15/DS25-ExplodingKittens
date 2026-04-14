package exploding_kittens.game.model;

/**
 * Questa classe appresenta una carta del gioco Exploding Kittens.
 * Ciascuna carta è caratterizzata da un tipo, un nome e una descrizione.
 * Le carte possono essere create specificando esplicitamente nome e descrizione,
 * oppure utilizzando il costruttore semplificato che genera automaticamente
 * valori di default a partire dal tipo.
 */
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
        this.name = type.name();
        this.description = "Carta di tipo " + type.name();
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