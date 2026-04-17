package explodingkittens.game.model;

/**
 * Enum contenente tutti i tipi di carte previsti dalla nostra versione semplificata del gioco.
 * Expording Kitten: carta che se pescata senza avere un Defuse in mano, comporta l'eliminazione del giocatore.
 * Defuse: carta che permette di disinnescare un Exploding Kittens e rimetterlo nel mazzo.
 * Skip: permette di saltare il turno.
 * Attack: Obbliga il giocatore successivo a giocare due turni.
 * Shuffle: permette di mescolare il mazzo.
 * See the future: permette al giocatore di vedere le prime tre carte in cima al mazzo.
 * Cat card: carta che giocata in coppia permette di rubare una carta random dalla mano di un avversario.
 */
public enum CardType {
    EXPLODING_KITTEN,
    DEFUSE,
    SKIP,
    ATTACK,
    SHUFFLE,
    SEE_THE_FUTURE,
    CAT_CARD
}
