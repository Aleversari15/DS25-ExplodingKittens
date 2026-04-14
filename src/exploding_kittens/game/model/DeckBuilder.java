package exploding_kittens.game.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class per la preparazione iniziale del mazzo.
 */
public class DeckBuilder {

    private DeckBuilder() {}

    /**
     * Metodo privato per la costruzione di un deck standard, senza exploding kittens che verranno
     * aggiunti successivamente
     * @param playerCount Numero di giocatori
     * @return una lista di carte (deck)
     */
    private static List<Card> createStandardDeck(int playerCount) {
        List<Card> cards = new ArrayList<>();

        for (int i = 0; i < playerCount + 1; i++)
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

    /**
     * Crea un deck standard e lo mescola.
     * @param numPlayers Numero di giocatori
     * @return deck mescolato
     */
    public static Deck prepareBaseDeck(int numPlayers) {
        List<Card> cards = createStandardDeck(numPlayers);
        Collections.shuffle(cards);
        Deck deck = new Deck();
        deck.setCards(cards);
        return deck;
    }

    /**
     * Questo metodo si occupa di aggiungere al deck le carte Exploding Kittens dopo che
     * sono state distribuite le carte ai giocatori.
     * Numero di exploding kittens = numero di giocatori + 1.
     * In questo modo rimarrà sempre un solo giocatore (il vincitore) prima della fine del mazzo.
     * @param deck
     * @param numPlayers numero di giocatori
     */
    public static void insertExplodingKittens(Deck deck, int numPlayers) {
        Random random = new Random();
        for (int i = 0; i < numPlayers +1; i++) {
            deck.insertCard(
                    new Card(CardType.EXPLODING_KITTEN, "Exploding Kitten", "Sei esploso!"),
                    random.nextInt(deck.size() + 1)
            );
        }
    }

    /**
     * Questo metodo che si occupa di distribuire le carte ai giocatori.
     * A ciascun giocatore viene data una carta Defuse + 4 carte prese dall cima del mazzo.
     * @param deck mazzo creato da cui prendere le carte da distribuire ai giocatori
     * @param players lista di giocatori a cui distribuire le carte
     * @return una map composta da <NomePlayer, lista carte>
     */
    public static Map<String, String> buildPlayerHands(Deck deck, List<Player> players) {
        Map<String, String> hands = new LinkedHashMap<>();
        for (Player player : players) {
            List<Card> hand = new ArrayList<>();
            Card defuse = deck.removeCardOfType(CardType.DEFUSE);
            if (defuse != null) hand.add(defuse);
            for (int i = 0; i < 4; i++) {
                hand.add(deck.removeTopCard());
            }
            String serialized = hand.stream()
                    .map(c -> c.getType().name())
                    .collect(Collectors.joining(","));
            hands.put(player.getAgentName(), serialized);
        }
        return hands;
    }



}