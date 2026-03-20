package exploding_kittens.game.view;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.imageio.ImageIO;

/**
 * GUI Swing per Exploding Kittens.
 * Le carte mostrano le immagini caricate da resources/.
 * Thread-safe tramite BlockingQueue per l'input utente.
 */
public class GameView {

    private static final Color BG_DARK       = new Color(18, 18, 28);
    private static final Color BG_PANEL      = new Color(28, 28, 42);
    private static final Color BG_CARD       = new Color(38, 38, 58);
    private static final Color ACCENT_ORANGE = new Color(255, 120, 40);
    private static final Color ACCENT_RED    = new Color(220, 60, 60);
    private static final Color ACCENT_GREEN  = new Color(60, 200, 120);
    private static final Color ACCENT_BLUE   = new Color(80, 160, 255);
    private static final Color ACCENT_PURPLE = new Color(180, 120, 255);
    private static final Color TEXT_PRIMARY  = new Color(240, 235, 220);
    private static final Color TEXT_MUTED    = new Color(140, 135, 120);
    private static final Color BORDER_COLOR  = new Color(60, 58, 80);


    private static final Font FONT_TITLE  = new Font("Georgia", Font.BOLD, 22);
    private static final Font FONT_LOG    = new Font("Monospaced", Font.PLAIN, 12);
    private static final Font FONT_BUTTON = new Font("Georgia", Font.BOLD, 12);
    private static final Font FONT_LABEL  = new Font("Georgia", Font.PLAIN, 13);
    private static final Font FONT_CARD_SM = new Font("Georgia", Font.BOLD, 9);

    private final Map<String, Image> cardImages = new HashMap<>();

    private JFrame    frame;
    private JPanel    handPanel;
    private JTextArea logArea;
    private JLabel    turnLabel;
    private JLabel    nicknameLabel;
    private JPanel    actionPanel;

    private final BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();

    public GameView() {
        loadCardImages();
        SwingUtilities.invokeLater(this::buildUI);
    }

    private void loadCardImages() {
        // Mappa CardType -> nome file img
        Map<String, String> fileNames = new HashMap<>();
        fileNames.put("EXPLODING_KITTEN", "ExplodingKitten.jpg");
        fileNames.put("DEFUSE",           "Defuse.jpg");
        fileNames.put("SKIP",             "Skip.jpg");
        fileNames.put("ATTACK",           "Attack.jpg");
        fileNames.put("SHUFFLE",          "Shuffle.jpg");
        fileNames.put("SEE_THE_FUTURE",   "SeetheFuture.jpg");
        fileNames.put("CAT_CARD",         "CartaGatto.jpg");

        for (Map.Entry<String, String> entry : fileNames.entrySet()) {
            try {
                File file = new File("src/exploding_kittens/resources/" + entry.getValue());
                if (file.exists()) {
                    BufferedImage img = ImageIO.read(file);
                    cardImages.put(entry.getKey(), img);
                } else {
                    System.err.println("[GameView] Immagine non trovata: " + file.getPath());
                }
            } catch (Exception e) {
                System.err.println("[GameView] Errore caricamento immagine: " + entry.getValue());
            }
        }
    }

    private void buildUI() {
        frame = new JFrame("Exploding Kittens" );
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(960, 700);
        frame.setMinimumSize(new Dimension(800, 580));
        frame.getContentPane().setBackground(BG_DARK);
        frame.setLayout(new BorderLayout(0, 0));

        frame.add(buildHeader(),      BorderLayout.NORTH);
        frame.add(buildLogPanel(),    BorderLayout.CENTER);
        frame.add(buildBottomPanel(), BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout(16, 0));
        panel.setBackground(BG_PANEL);
        panel.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 2, 0, ACCENT_ORANGE),
                new EmptyBorder(14, 20, 14, 20)
        ));

        JLabel title = new JLabel("EXPLODING KITTENS");
        title.setFont(FONT_TITLE);
        title.setForeground(ACCENT_ORANGE);

        nicknameLabel = new JLabel("");
        nicknameLabel.setFont(FONT_LABEL);
        nicknameLabel.setForeground(TEXT_MUTED);
        nicknameLabel.setHorizontalAlignment(SwingConstants.CENTER);

        turnLabel = new JLabel("In attesa...");
        turnLabel.setFont(FONT_LABEL);
        turnLabel.setForeground(ACCENT_BLUE);
        turnLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        panel.add(title,         BorderLayout.WEST);
        panel.add(nicknameLabel, BorderLayout.CENTER);
        panel.add(turnLabel,     BorderLayout.EAST);
        return panel;
    }

    private JPanel buildLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(12, 16, 0, 16));

        JLabel logTitle = new JLabel("-- REGISTRO DI GIOCO --");
        logTitle.setFont(new Font("Georgia", Font.BOLD, 11));
        logTitle.setForeground(TEXT_MUTED);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(BG_PANEL);
        logArea.setForeground(TEXT_PRIMARY);
        logArea.setFont(FONT_LOG);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setBorder(new EmptyBorder(10, 12, 10, 12));

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(new LineBorder(BORDER_COLOR, 1));
        scroll.getVerticalScrollBar().setBackground(BG_PANEL);

        panel.add(logTitle, BorderLayout.NORTH);
        panel.add(scroll,   BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(10, 16, 16, 16));

        JLabel handTitle = new JLabel("LA TUA MANO: ");
        handTitle.setFont(new Font("Georgia", Font.BOLD, 11));
        handTitle.setForeground(TEXT_MUTED);
        handTitle.setBorder(new EmptyBorder(0, 0, 6, 0));

        handPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        handPanel.setBackground(BG_DARK);
        handPanel.setPreferredSize(new Dimension(0, 170));

        JPanel handWrapper = new JPanel(new BorderLayout());
        handWrapper.setBackground(BG_DARK);
        handWrapper.add(handTitle, BorderLayout.NORTH);
        handWrapper.add(handPanel, BorderLayout.CENTER);

        // Bottoni azione
        actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        actionPanel.setBackground(BG_DARK);

        JButton drawBtn    = buildButton("PESCA",          ACCENT_GREEN);
        JButton skipBtn    = buildButton("SKIP",            ACCENT_BLUE);
        JButton attackBtn  = buildButton("ATTACK",          ACCENT_RED);
        JButton shuffleBtn = buildButton("SHUFFLE",         ACCENT_ORANGE);
        JButton futureBtn  = buildButton("SEE THE FUTURE",  ACCENT_PURPLE);
        JButton catBtn     = buildButton("CAT CARD",        ACCENT_ORANGE);

        drawBtn.addActionListener(e    -> inputQueue.offer("DRAW"));
        skipBtn.addActionListener(e    -> inputQueue.offer("PLAY:SKIP"));
        attackBtn.addActionListener(e  -> inputQueue.offer("PLAY:ATTACK"));
        shuffleBtn.addActionListener(e -> inputQueue.offer("PLAY:SHUFFLE"));
        futureBtn.addActionListener(e  -> inputQueue.offer("PLAY:SEE_THE_FUTURE"));
        catBtn.addActionListener(e     -> askCatCardTarget());

        actionPanel.add(drawBtn);
        actionPanel.add(skipBtn);
        actionPanel.add(attackBtn);
        actionPanel.add(shuffleBtn);
        actionPanel.add(futureBtn);
        actionPanel.add(catBtn);

        setActionsEnabled(false);

        panel.add(handWrapper, BorderLayout.CENTER);
        panel.add(actionPanel, BorderLayout.SOUTH);
        return panel;
    }


    private JButton buildButton(String text, Color accent) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isPressed()  ? accent.darker().darker() :
                        getModel().isRollover() ? accent.darker() : BG_CARD;
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                g2.setColor(isEnabled() ? accent : TEXT_MUTED);
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 10, 10));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(FONT_BUTTON);
        btn.setForeground(accent);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(8, 16, 8, 16));
        btn.setOpaque(false);
        return btn;
    }


    private JPanel buildCardPanel(String cardType) {
        Color accent = cardAccent(cardType);
        Image img    = cardImages.get(cardType.trim());

        // Scala l'immagine
        final Image scaledImg = (img != null)
                ? img.getScaledInstance(100, 120, Image.SCALE_SMOOTH)
                : null;

        JPanel card = new JPanel(new BorderLayout(0, 3)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 12, 12));
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(110, 170));
        card.setBorder(new EmptyBorder(4, 4, 4, 4));

        if (scaledImg != null) {
            JLabel imgLabel = new JLabel(new ImageIcon(scaledImg));
            imgLabel.setHorizontalAlignment(SwingConstants.CENTER);
            card.add(imgLabel, BorderLayout.CENTER);
        } else {
            //Testo mostrato se ci sono problemi con l'immagine
            JLabel fallback = new JLabel(cardName(cardType), SwingConstants.CENTER);
            fallback.setFont(new Font("Georgia", Font.BOLD, 9));
            fallback.setForeground(accent);
            card.add(fallback, BorderLayout.CENTER);
        }

        // Nome carta in basso
        JLabel nameLabel = new JLabel(
                "<html><center>" + cardName(cardType) + "</center></html>",
                SwingConstants.CENTER
        );
        nameLabel.setFont(FONT_CARD_SM);
        nameLabel.setForeground(accent);
        card.add(nameLabel, BorderLayout.SOUTH);

        return card;
    }



    public void showWelcome(String nickname) {
        SwingUtilities.invokeLater(() -> {
            nicknameLabel.setText("Giocatore: " + nickname);
            appendLog("Benvenuto, " + nickname + "!");
        });
    }

    public void showWaitingForPlayers() {
        SwingUtilities.invokeLater(() -> {
            turnLabel.setText("In attesa dei giocatori...");
            turnLabel.setForeground(TEXT_MUTED);
            appendLog("[Sistema] In attesa degli altri giocatori...");
        });
    }

    public void showGameStarted() {
        SwingUtilities.invokeLater(() ->
                appendLog("\n====== LA PARTITA E' INIZIATA! ======\n"));
    }

    public void showGameOver(String winnerNickname) {
        SwingUtilities.invokeLater(() -> {
            appendLog("\n[FINE] Vince: " + winnerNickname + "!");
            turnLabel.setText("Vince: " + winnerNickname);
            turnLabel.setForeground(ACCENT_ORANGE);
            setActionsEnabled(false);
            JOptionPane.showMessageDialog(frame,
                    winnerNickname + " ha vinto la partita!",
                    "Partita terminata", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    public void showYourTurn() {
        SwingUtilities.invokeLater(() -> {
            turnLabel.setText(">>> E' il tuo turno! <<<");
            turnLabel.setForeground(ACCENT_GREEN);
            appendLog("\n[TURNO] E' il tuo turno!");
        });
    }

    public void showOtherPlayerTurn(String nickname) {
        SwingUtilities.invokeLater(() -> {
            turnLabel.setText("Turno di: " + nickname);
            turnLabel.setForeground(ACCENT_BLUE);
            appendLog("[TURNO] Sta giocando: " + nickname);
            setActionsEnabled(false);
        });
    }

    public void showHand(List<String> cardNames) {
        SwingUtilities.invokeLater(() -> {
            handPanel.removeAll();
            for (String card : cardNames) {
                if (!card.isBlank()) handPanel.add(buildCardPanel(card.trim()));
            }
            handPanel.revalidate();
            handPanel.repaint();
            setActionsEnabled(true);
        });
    }

    public void showCardDrawn(String cardType) {
        SwingUtilities.invokeLater(() -> {
            appendLog("[Pesca] Hai pescato: " + cardName(cardType));
            setActionsEnabled(false);
        });
    }

    public void showCardPlayed(String nickname, String cardType) {
        SwingUtilities.invokeLater(() ->
                appendLog("[Giocata] " + nickname + " ha giocato: " + cardName(cardType)));
    }

    public void showStolenCard(String cardType) {
        SwingUtilities.invokeLater(() ->
                appendLog("[Furto] Ti e' stata rubata: " + cardName(cardType)));
    }

    public void showSeeTheFuture(List<String> top3) {
        SwingUtilities.invokeLater(() -> {
            appendLog("[See the Future] Prossime 3 carte del mazzo:");
            for (int i = 0; i < top3.size(); i++) {
                String c = top3.get(i).trim();
                if (!c.isEmpty()) appendLog("   " + (i + 1) + ". " + cardName(c));
            }
        });
    }

    public void showExplosion() {
        SwingUtilities.invokeLater(() -> {
            appendLog("\n[!!!] HAI PESCATO UN EXPLODING KITTEN! [!!!]\n");
            turnLabel.setText("!!! EXPLODING KITTEN !!!");
            turnLabel.setForeground(ACCENT_RED);
        });
    }

    public void showDefuseUsed() {
        SwingUtilities.invokeLater(() ->
                appendLog("[Defuse] Hai usato il Defuse. Sei salvo!"));
    }

    public void showEliminated(String nickname) {
        SwingUtilities.invokeLater(() ->
                appendLog("[Eliminato] " + nickname + " e' stato eliminato!"));
    }

    public void showYouAreEliminated() {
        SwingUtilities.invokeLater(() -> {
            appendLog("\n[ELIMINATO] Sei stato eliminato! Nessun Defuse disponibile.\n");
            turnLabel.setText("Sei eliminato");
            turnLabel.setForeground(ACCENT_RED);
            setActionsEnabled(false);
            JOptionPane.showMessageDialog(frame,
                    "Sei stato eliminato!\nNessun Defuse disponibile.",
                    "Eliminato", JOptionPane.WARNING_MESSAGE);
        });
    }

    public void showError(String message) {
        SwingUtilities.invokeLater(() -> appendLog("[Errore] " + message));
    }

    public void showNotYourTurn() {
        SwingUtilities.invokeLater(() -> appendLog("[Errore] Non e' il tuo turno!"));
    }

    public void showCardNotInHand() {
        SwingUtilities.invokeLater(() -> appendLog("[Errore] Non hai quella carta in mano."));
    }

    public void showShuffled() {
        SwingUtilities.invokeLater(() -> appendLog("[Shuffle] Il mazzo e' stato mischiato."));
    }

    public String askAction() {
        SwingUtilities.invokeLater(() -> setActionsEnabled(true));
        try {
            return inputQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "DRAW";
        }
    }

    public int askDefusePosition(int deckSize) {
        SwingUtilities.invokeLater(() -> {
            String input = JOptionPane.showInputDialog(frame,
                    "In che posizione vuoi reinserire il Kitten?\n(0 = cima del mazzo)",
                    "Defuse", JOptionPane.QUESTION_MESSAGE);
            inputQueue.offer("__POS__:" + (input != null ? input.trim() : "0"));
        });
        try {
            String raw = inputQueue.take();
            return Math.max(0, Integer.parseInt(raw.replace("__POS__:", "").trim()));
        } catch (Exception e) {
            return 0;
        }
    }

    public String askNickname() {
        String[] result = { "Giocatore" };
        try {
            SwingUtilities.invokeAndWait(() -> {
                String input = JOptionPane.showInputDialog(null,
                        "Inserisci il tuo nickname:",
                        "Exploding Kittens", JOptionPane.QUESTION_MESSAGE);
                result[0] = (input != null && !input.isBlank()) ? input.trim() : "Giocatore";
            });
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }



    private void askCatCardTarget() {
        String target = JOptionPane.showInputDialog(frame,
                "Nome del giocatore da cui vuoi rubare:",
                "Cat Card", JOptionPane.QUESTION_MESSAGE);
        if (target != null && !target.isBlank()) {
            inputQueue.offer("CAT_CARD:" + target.trim());
        }
    }

    private void setActionsEnabled(boolean enabled) {
        for (Component c : actionPanel.getComponents()) c.setEnabled(enabled);
    }

    private void appendLog(String text) {
        logArea.append(text + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private String cardName(String cardType) {
        return switch (cardType.trim()) {
            case "EXPLODING_KITTEN" -> "Exploding Kitten";
            case "DEFUSE"           -> "Defuse";
            case "SKIP"             -> "Skip";
            case "ATTACK"           -> "Attack";
            case "SHUFFLE"          -> "Shuffle";
            case "SEE_THE_FUTURE"   -> "See the Future";
            case "CAT_CARD"         -> "Cat Card";
            default                 -> cardType;
        };
    }

    private Color cardAccent(String cardType) {
        return switch (cardType.trim()) {
            case "EXPLODING_KITTEN" -> ACCENT_RED;
            case "DEFUSE"           -> ACCENT_GREEN;
            case "SKIP"             -> ACCENT_BLUE;
            case "ATTACK"           -> ACCENT_RED;
            case "SHUFFLE"          -> ACCENT_ORANGE;
            case "SEE_THE_FUTURE"   -> ACCENT_PURPLE;
            case "CAT_CARD"         -> ACCENT_ORANGE;
            default                 -> TEXT_MUTED;
        };
    }
}
