package exploding_kittens.game.model;

/**
 * Questa classe rappresenta un giocatore all'interno della partita.
 * Ogni giocatore è identificato da:
 * - agentName: nome dell'agente JADE (univoco nel sistema)
 * - nickname: nome leggibile mostrato all'utente, scelto dal giocatore nel momento in cui si unisce alla partita.
 */
public class Player {
    private final String agentName;
    private final String nickname;

    /**
     * Costruttore della classe Player.
     * @param agentName nome dell'agente JADE.
     * @param nickname nome del giocatore visibile agli altri utenti.
     */
    public Player(String agentName, String nickname) {
        this.agentName = agentName;
        this.nickname = nickname;
    }

    /**
     * Restituisce il nome dell'agente Jade.
     * @return il nome dell'agente Jade.
     */
    public String getAgentName() { return agentName; }

    /**
     * Restituisce il nickname del giocatore.
     * @return il nickname.
     */
    public String getNickname() { return nickname; }
}
