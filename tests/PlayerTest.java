package tests;

import exploding_kittens.game.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerTest {

    private Player player;

    @BeforeEach
    void setUp() {
        player = new Player("Agent1", "Player1");
    }
    @Test
    void testPlayerInitialization() {
        assertEquals("Player1", player.getNickname());
        assertEquals("Agent1", player.getAgentName());
    }
}
