package explodingkittens.remote;

import explodingkittens.game.model.Deck;
import explodingkittens.game.model.DeckBuilder;
import explodingkittens.game.model.GameState;
import explodingkittens.game.model.Player;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Classe di test per verificare la consistenza della serializzazione e deserializzazione dello stato di gioco.
 */
public class GameStateSerializationConsistencyTest {

    /**
     * Sottoclasse concreta di {@link AbstractMasterAgent} utilizzata esclusivamente a scopo di test.
     * Permette di iniettare e leggere lo stato del gioco (GameState e Deck) per simulare
     * il comportamento degli agenti reali.
     */
    private class TestableMasterAgent extends AbstractMasterAgent {
        public void setGameState(GameState gs) { this.gameState = gs; }
        public void setDeck(Deck d) { this.deck = d; }
        public GameState getGameState() { return this.gameState; }
    }

/**
 * Verifica che uno stato di gioco, una volta serializzato e
 * successivamente ricostruito, mantenga l'integrità di tutti i campi fondamentali.
 * */
    @Test
    void TestSerializationConsistency(){
        TestableMasterAgent agent = new TestableMasterAgent();
        TestableMasterAgent agent2 = new TestableMasterAgent();
        GameState originalState = new GameState();
        originalState.addPlayer(new Player("A1", "P1"));
        originalState.addPlayer(new Player("A2", "P2"));
        originalState.setCurrentPlayerIndex(0);
        originalState.setTurnsToPlay(2);

        agent.setGameState(originalState);
        agent.setDeck(DeckBuilder.prepareBaseDeck(2));

        String serializedState = agent.serializeState();
        agent2.reconstructState(serializedState);

        Assertions.assertEquals(agent.getGameState().getCurrentPlayerIndex(), agent2.getGameState().getCurrentPlayerIndex());
        Assertions.assertEquals(agent.getGameState().getTurnsToPlay(), agent2.getGameState().getTurnsToPlay());
        Assertions.assertEquals(agent.getGameState().getActivePlayers().getFirst().getNickname(), agent2.getGameState().getActivePlayers().getFirst().getNickname());

    }

}
