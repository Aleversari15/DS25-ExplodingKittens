package tests;


import exploding_kittens.game.model.GameState;
import exploding_kittens.game.model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameStateTest {

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
    void testNextPlayer() {
        assertEquals(gameState.getCurrentPlayer(), player1);
        gameState.nextTurn();
        assertEquals(gameState.getCurrentPlayer(), player2);
        gameState.nextTurn();
        assertEquals(gameState.getCurrentPlayer(), player3);
        gameState.nextTurn();
        assertEquals(gameState.getCurrentPlayer(), player1);
    }

    @Test
    void testAddPlayer() {
        Player player4 = new Player("Agent4", "Player4");
        gameState.addPlayer(player4);
        assertTrue(gameState.getActivePlayers().contains(player4));
        gameState.removePlayer(player4);
    }

    @Test
    void testWinner() {
        gameState.removePlayer(player1);
        gameState.removePlayer(player2);
        assertTrue(gameState.isGameOver());
        assertEquals(gameState.getWinner(), player3);
    }

}