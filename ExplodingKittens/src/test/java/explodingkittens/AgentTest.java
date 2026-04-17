package explodingkittens;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;


public class AgentTest {
    /*
    private Runtime rt;
    private AgentContainer container;

    @BeforeEach
    void setUp() {
        rt = Runtime.instance();
        // reset del Runtime per evitare che thread residui blocchino il nuovo container
        rt.setCloseVM(false);

        Profile p = new ProfileImpl();
        // porta casuale per evitare conflitti con altri test o istanze di JADE
        p.setParameter(Profile.MAIN_PORT, String.valueOf(1099 + (int)(Math.random() * 100)));
        container = rt.createMainContainer(p);
    }

    @AfterEach
    void tearDown() {
        try {
            if (container != null) {
                container.kill();
                // Piccola attesa per permettere al sistema operativo di liberare la porta
                Thread.sleep(500);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    @DisplayName("Test di Join: Il Master deve confermare la registrazione del Player e mettersi in attesa del secondo giocatore")
    public void testPlayerJoinCommunication() throws Exception {

        AgentController master = container.createNewAgent(
                "GameMasterTest",
                "explodingkittens.remote.GameMasterAgent",
                new Object[]{"2"} // il gameMater aspetta 2 giocatori
        );
        master.start();

        // Aspettiamo che il Master si registri
        Thread.sleep(1000);

        // Crea un Player che si unisce al Master
        AgentController player = container.createNewAgent(
                "Giocatore1Test",
                "explodingkittens.remote.PlayerAgent",
                new Object[]{"Giocatore 1", 2}
        );
        player.start();

        Thread.sleep(2000);

        // la comunicazione base è stabilita.
        assertEquals("Idle", master.getState().getName(), "Il Master dovrebbe essere nello stato Idle in attesa di giocatori");
    }

    @Test
    @DisplayName("Test di Partita: Avvio con due giocatori")
    public void testGameStartWithTwoPlayers() throws Exception {

        AgentController master = container.createNewAgent(
                "GameMasterTest",
                "explodingkittens.remote.GameMasterAgent",
                new Object[]{"2"}
        );
        master.start();

        Thread.sleep(1000);

        //primo giocatore
        AgentController player1 = container.createNewAgent(
                "Giocatore1Test",
                "explodingkittens.remote.PlayerAgent",
                new Object[]{"Giocatore 1", 2}
        );
        player1.start();

        Thread.sleep(1000);

        //secondo giocatore
        AgentController player2 = container.createNewAgent(
                "Giocatore2Test",
                "explodingkittens.remote.PlayerAgent",
                new Object[]{"Giocatore 2", 2}
        );
        player2.start();

        // Aspettiamo alcuni secondi per l'inizializzazione del mazzo, mani e logica di gioco
        Thread.sleep(3000);

        assertNotNull(master.getState(), "Lo stato del Master non dovrebbe essere nullo");
        assertNotNull(player1.getState(), "Lo stato del Giocatore 1 non dovrebbe essere nullo");
        assertNotNull(player2.getState(), "Lo stato del Giocatore 2 non dovrebbe essere nullo");
    }
*/
}

