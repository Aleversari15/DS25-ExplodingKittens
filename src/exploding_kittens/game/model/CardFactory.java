package exploding_kittens.game.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Permette di creare il mazzo di partenza, composto nel seguente modo:
 * - Numero di Exploding Kittens pari al numero di giocatori -1
 * - Numero di carte Defuse pari al numero di giocatori + 2
 * - 4 carte azione per ogni tipo (skip, attack, shuffle e see the future)
 * - 8 carte gatto (utili solo se utilizzate in coppia)
 */
public class CardFactory {

    public static List<Card> createStandardDeck(int playerCount) {
        List<Card> cards = new ArrayList<>();

        for (int i = 0; i < playerCount - 1; i++)
            cards.add(new Card(CardType.EXPLODING_KITTEN, "Exploding Kitten", "Esplodi! (A meno che tu non abbia una carta Defuse)"));

        for (int i = 0; i < playerCount + 2; i++)
            cards.add(new Card(CardType.DEFUSE, "Defuse", "Neutralizza un Exploding Kitten"));

        for (int i = 0; i < 4; i++)
            cards.add(new Card(CardType.SKIP, "Skip", "Termina il tuo turno senza pescare"));
        for (int i = 0; i < 4; i++)
            cards.add(new Card(CardType.ATTACK, "Attack", "Forza il prossimo giocatore a giocare due turni"));
        for (int i = 0; i < 4; i++)
            cards.add(new Card(CardType.SHUFFLE, "Shuffle", "Mischia il mazzo"));
        for (int i = 0; i < 4; i++)
            cards.add(new Card(CardType.SEE_THE_FUTURE, "See the Future", "Pesca e guarda le prime 3 carte del mazzo"));

        for (int i = 0; i < 8; i++)
            cards.add(new Card(CardType.CAT_CARD, "Cat Card", "Se utilizzata insieme ad un'altra carta Gatto permette di pescare una carta random dalla mano di un altro giocatore"));

        return cards;
    }
}

