package exploding_kittens.tests;

import exploding_kittens.game.model.GameState;
import exploding_kittens.game.model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CardFactoryTest {

    private GameState gameState;
    private Player player1, player2, player3;

    @BeforeEach
    void setUp() {
        gameState = new GameState();
        player1 = new Player("Agent1", "Player1");
        player2 = new Player("Agent2", "Player2");
        player3 = new Player("Agent3", "Player3");

        gameState.addPlayer(player1);
        gameState.addPlayer(player2);
        gameState.addPlayer(player3);
    }

    @Test
    void testInitialCurrentPlayer() {
        assertEquals(player1, gameState.getCurrentPlayer());
    }

    @Test
    void testDeckSize() {

    }

    @Test
    void explodingKittenCount() {
        setUp();
        //TODO
    }

}