package exploding_kittens.game.view;

import java.util.List;
import java.util.Scanner;

/**
 * View testuale minimale utilizzata per test iniziali.
 */
public class GameView {

    private final Scanner scanner = new Scanner(System.in);

    public void showWelcome(String nickname) {
        System.out.println("==========================================");
        System.out.println("  Benvenuto in Exploding Kittens, " + nickname + "!");
        System.out.println("==========================================");
    }

    public void showWaitingForPlayers() {
        System.out.println("[Sistema] In attesa degli altri giocatori...");
    }

    public void showGameStarted() {
        System.out.println("\n========== LA PARTITA È INIZIATA! ==========\n");
    }

    public void showGameOver(String winnerNickname) {
        System.out.println("\n==========================================");
        System.out.println("  PARTITA TERMINATA! Vince: " + winnerNickname);
        System.out.println("==========================================\n");
    }


    public void showYourTurn() {
        System.out.println("\n---------- È il tuo turno! ----------");
    }

    public void showOtherPlayerTurn(String nickname) {
        System.out.println("\n[Turno] Sta giocando: " + nickname);
    }

    public void showHand(List<String> cardNames) {
        System.out.println("La tua mano:");
        for (int i = 0; i < cardNames.size(); i++) {
            System.out.println("  [" + i + "] " + cardNames.get(i));
        }
    }

    public void showCardDrawn(String cardType) {
        System.out.println("[Pesca] Hai pescato: " + formatCard(cardType));
    }

    public void showCardPlayed(String nickname, String cardType) {
        System.out.println("[Giocata] " + nickname + " ha giocato: " + formatCard(cardType));
    }

    public void showStolenCard(String cardType) {
        System.out.println("[Furto] Ti è stata rubata: " + formatCard(cardType));
    }

    public void showSeeTheFuture(List<String> top3) {
        System.out.println("[See the Future] Prossime 3 carte del mazzo:");
        for (int i = 0; i < top3.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + formatCard(top3.get(i)));
        }
    }

    public void showExplosion() {
        System.out.println("\n💣 HAI PESCATO UN EXPLODING KITTEN! 💣");
    }

    public void showDefuseUsed() {
        System.out.println("[Defuse] Hai usato il Defuse. Sei salvo!");
    }

    public void showEliminated(String nickname) {
        System.out.println("[Eliminato] " + nickname + " è stato eliminato!");
    }

    public void showYouAreEliminated() {
        System.out.println("\n💀 Sei stato eliminato! Nessun Defuse disponibile.");
    }


    public void showError(String message) {
        System.out.println("[Errore] " + message);
    }

    public void showNotYourTurn() {
        System.out.println("[Errore] Non è il tuo turno!");
    }

    public void showCardNotInHand() {
        System.out.println("[Errore] Non hai quella carta in mano.");
    }

    public void showShuffled() {
        System.out.println("[Shuffle] Il mazzo è stato mischiato.");
    }


    public String askAction() {
        System.out.println("\nCosa vuoi fare?");
        System.out.println("  [D] Pesca una carta");
        System.out.println("  [P] Gioca una carta");
        System.out.print("> ");
        String choice = scanner.nextLine().trim().toUpperCase();

        if (choice.equals("D")) {
            return "DRAW";
        } else if (choice.equals("P")) {
            return askCardToPlay();
        } else {
            System.out.println("Scelta non valida, riprova.");
            return askAction();
        }
    }

    private String askCardToPlay() {
        System.out.println("\nQuale carta vuoi giocare?");
        System.out.println("  [1] Skip");
        System.out.println("  [2] Attack");
        System.out.println("  [3] Shuffle");
        System.out.println("  [4] See the Future");
        System.out.println("  [5] Cat Card (coppia)");
        System.out.print("> ");
        String choice = scanner.nextLine().trim();

        return switch (choice) {
            case "1" -> "PLAY:SKIP";
            case "2" -> "PLAY:ATTACK";
            case "3" -> "PLAY:SHUFFLE";
            case "4" -> "PLAY:SEE_THE_FUTURE";
            case "5" -> {
                System.out.print("Nome del giocatore da cui rubare: ");
                String target = scanner.nextLine().trim();
                yield "CAT_CARD:" + target;
            }
            default -> {
                System.out.println("Scelta non valida, riprova.");
                yield askCardToPlay();
            }
        };
    }

    /**
     * Chiede all'utente in che posizione reinserire il Kitten dopo il Defuse.
     */
    public int askDefusePosition(int deckSize) {
        System.out.println("In che posizione vuoi reinserire il Kitten? (0 = cima, " + deckSize + " = fondo)");
        System.out.print("> ");
        try {
            return Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Inserisci un numero valido.");
            return askDefusePosition(deckSize);
        }
    }

    /**
     * Chiede il nickname all'avvio.
     */
    public String askNickname() {
        System.out.print("Inserisci il tuo nickname: ");
        return scanner.nextLine().trim();
    }


    private String formatCard(String cardType) {
        return switch (cardType) {
            case "EXPLODING_KITTEN" -> "💣 Exploding Kitten";
            case "DEFUSE"           -> "🛡️  Defuse";
            case "SKIP"             -> "⏭️  Skip";
            case "ATTACK"           -> "⚔️  Attack";
            case "SHUFFLE"          -> "🔀 Shuffle";
            case "SEE_THE_FUTURE"   -> "👁️  See the Future";
            case "CAT_CARD"         -> "🐱 Cat Card";
            default                 -> cardType;
        };
    }
}
