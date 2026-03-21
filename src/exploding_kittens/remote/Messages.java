package exploding_kittens.remote;

public final class Messages {

    //TODO rimettere in ordine la suddivisione in sezioni al termine del progetto
    private Messages() {}

    //Registrazione
    public static final String JOIN              = "JOIN";
    public static final String JOINED            = "JOINED";

    //Gestione turno
    public static final String YOUR_TURN         = "YOUR_TURN";
    public static final String TURN_OF           = "TURN:";
    public static final String NOT_YOUR_TURN     = "NOT_YOUR_TURN";

    //Azioni giocatore
    public static final String DRAW              = "DRAW";
    public static final String PLAY              = "PLAY:";
    public static final String CAT_CARD_PLAY     = "CAT_CARD:";
    public static final String DEFUSE_PLAY       = "DEFUSE:";

    //Risposte GameMaster
    public static final String DREW              = "DREW:";
    public static final String DREW_KITTEN       = "DREW:EXPLODING_KITTEN";
    public static final String CARD_NOT_IN_HAND  = "CARD_NOT_IN_HAND";
    public static final String INVALID_TARGET    = "INVALID_TARGET";
    public static final String MISSING_TARGET    = "MISSING_TARGET";
    public static final String SKIP_OK           = "SKIP_OK";
    public static final String ATTACK_OK         = "ATTACK_OK";
    public static final String SHUFFLE_OK        = "SHUFFLE_OK";
    public static final String SEE_THE_FUTURE    = "SEE_THE_FUTURE:";
    public static final String STOLEN            = "STOLEN:";
    public static final String DEFUSED           = "DEFUSED";
    public static final String WINNER            = "WINNER:";
    public static final String REFRESH_HAND = "REFRESH_HAND";
    public static final String REQUEST_HAND     = "REQUEST_HAND";
    public static final String HAND_RESPONSE  = "HAND_RESPONSE:";

    //Messaggi tra player e suoi sottoagenti
    public static final String HAND_INIT         = "HAND:";
    public static final String GET_HAND          = "GET_HAND";
    public static final String ADD_CARD          = "ADD_CARD:";
    public static final String REMOVE_CARD       = "REMOVE_CARD:";
    public static final String HAND_READY = "HAND_READY";
    public static final String REFRESH_HAND_AFTER_DEFUSE = "REFRESH_HAND_AFTER_DEFUSE";


    // Messaggi  da KittenDefenseAgent a PlayerAgent per la UI
    public static final String SHOW_DEFUSE_USED      = "SHOW_DEFUSE_USED";
    public static final String SHOW_ELIMINATED       = "SHOW_ELIMINATED";
    public static final String ASK_DEFUSE_POSITION   = "ASK_DEFUSE_POSITION";

    //Messaggi PlayerAgent- KittenDefenseAgent
    public static final String KITTEN_DRAWN      = "EXPLODING_KITTEN_DRAWN";
    public static final String PLAYER_ELIMINATED = "PLAYER_ELIMINATED";


    //Messaggi KittenDefenseAgent ↔ HandManagerAgent
    public static final String HAS_DEFUSE_ASK    = "HAS_DEFUSE?";
    public static final String HAS_DEFUSE_YES    = "HAS_DEFUSE:YES";
    public static final String HAS_DEFUSE_NO     = "HAS_DEFUSE:NO";
    public static final String USE_DEFUSE        = "USE_DEFUSE";

    public static final String YOU_STOLE        = "YOU_STOLE:";
    public static final String STOLEN_FROM_YOU  = "STOLEN_FROM_YOU:";
}