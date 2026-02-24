package exploding_kittens.game.model;

public class Player {
    private final String agentName;
    private final String nickname;

    public Player(String agentName, String nickname) {
        this.agentName = agentName;
        this.nickname = nickname;
    }

    public String getAgentName() { return agentName; }
    public String getNickname() { return nickname; }
}
