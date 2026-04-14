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

    /**
     * Costruttore base per la creazione di una carta.
     * @param type tipo della carta
     * @param name nome della carta
     * @param description descrizione della carta
     */
    public Card(CardType type, String name, String description) {
        this.type = type;
        this.name = name;
        this.description = description;
    }

    /**
     * Altro costrutture per la creazione di una carta, che richiede solo in parametro.
     * @param type tipo della carta.
     */
    public Card(CardType type) {
        this.type = type;
        this.name = type.name();
        this.description = "Carta di tipo " + type.name();
    }

    /**
     * Restituisce il tipo della carta.
     * @return il tipo
     */
    public CardType getType() {
        return type;
    }

    /**
     * Restituisce il nome della carta.
     * @return il nome
     */
    @Override
    public String toString() {
        return name;
    }
}