package explodingkittens.game.view;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
 * GUI Swing per singolo giocatore.
 * Le carte mostrano le immagini caricate da resources/.
 * Mostra le carte che il giocatore ha in mano e in 
 * formato testuale mostra tutte le azioni e i cambi di turno della partita.
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
    private boolean isPlayerTurn = false;
    private JPanel    playersPanel;
    private static final Map<String, String> CARD_DESCRIPTIONS = Map.of(
            "EXPLODING_KITTEN", "Esplodi! A meno che tu non abbia un Defuse.",
            "DEFUSE",           "Neutralizza un Exploding Kitten.",
            "SKIP",             "Termina il tuo turno senza pescare.",
            "ATTACK",           "Termina il tuo turno e il prossimo giocatore deve fare 2 turni.",
            "SHUFFLE",          "Mescola il mazzo.",
            "SEE_THE_FUTURE",   "Guarda le prime 3 carte del mazzo.",
            "CAT_CARD",         "Usala in coppia per rubare una carta a un avversario."
    );
    private DefaultListModel<String> playersListModel;
    private JList<String> playersList;
    private final BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();

    public GameView() {
        loadCardImages();
        SwingUtilities.invokeLater(this::buildUI);
    }

    private void loadCardImages() {
        Map<String, String> fileNames = getStringImageNameMap();
        for (Map.Entry<String, String> entry : fileNames.entrySet()) {
            try {
                java.io.InputStream is = getClass().getResourceAsStream("/" + entry.getValue());
                if (is != null) {
                    BufferedImage img = ImageIO.read(is);
                    cardImages.put(entry.getKey(), img);
                } else {
                    System.err.println("[GameView] Immagine non trovata nel classpath: /" + entry.getValue());
                }
            } catch (Exception e) {
                System.err.println("[GameView] Errore caricamento immagine: " + entry.getValue());
                e.printStackTrace();
            }
        }
    }

    private static Map<String, String> getStringImageNameMap() {
        Map<String, String> fileNames = new HashMap<>();
        fileNames.put("EXPLODING_KITTEN",  "ExplodingKitten.jpg");
        fileNames.put("DEFUSE",            "Defuse.jpg");
        fileNames.put("SKIP",              "Skip.jpg");
        fileNames.put("ATTACK",            "Attack.jpg");
        fileNames.put("SHUFFLE",           "Shuffle.jpg");
        fileNames.put("SEE_THE_FUTURE",    "SeetheFuture.jpg");
        fileNames.put("CAT_CARD",          "CartaGatto.jpg");
        fileNames.put("WINNER",            "winner.png") ;
        return fileNames;
    }

    private static Map<String, String> getCardDescriptionsMap() {
        Map<String, String> cardDescriptions = new HashMap<>();
        cardDescriptions.put("DEFUSE",               "Neutralizza un Exploding Kitten.");
        cardDescriptions.put("SKIP",                 "Termina il tuo turno senza pescare.");
        cardDescriptions.put("ATTACK",               "Termina il tuo turno e il prossimo giocatore deve fare 2 turni.");
        cardDescriptions.put("SHUFFLE",              "Mescola il mazzo.");
        cardDescriptions.put("SEE_THE_FUTURE",       "Guarda le prime 3 carte del mazzo.");
        cardDescriptions.put(  "CAT_CARD",           "Usala in coppia per rubare una carta a un avversario.");
        return cardDescriptions;
    }


    private void buildUI() {
        frame = new JFrame("Exploding Kittens");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(960, 700);
        frame.setMinimumSize(new Dimension(800, 580));
        frame.getContentPane().setBackground(BG_DARK);
        frame.setLayout(new BorderLayout(0, 0));

        JPanel mainContainer = new JPanel(new BorderLayout(10, 0));
        mainContainer.setBackground(BG_DARK);

        mainContainer.add(buildPlayersSidebar(), BorderLayout.WEST);
        mainContainer.add(buildLogPanel(), BorderLayout.CENTER);

        frame.add(buildHeader(), BorderLayout.NORTH);
        frame.add(mainContainer, BorderLayout.CENTER);
        frame.add(buildBottomPanel(), BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel buildPlayersSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 10));
        sidebar.setBackground(BG_DARK);
        sidebar.setPreferredSize(new Dimension(180, 0));
        sidebar.setBorder(new EmptyBorder(12, 16, 0, 0));

        JLabel sideTitle = new JLabel("GIOCATORI IN VITA");
        sideTitle.setFont(new Font("Georgia", Font.BOLD, 11));
        sideTitle.setForeground(TEXT_MUTED);

        playersListModel = new DefaultListModel<>();
        playersList = new JList<>(playersListModel);
        playersList.setBackground(BG_PANEL);
        playersList.setForeground(TEXT_PRIMARY);
        playersList.setFont(FONT_LABEL);
        playersList.setFixedCellHeight(30);
        playersList.setBorder(new EmptyBorder(5, 5, 5, 5));
        playersList.setSelectionModel(new DefaultListSelectionModel() {
            @Override public void setSelectionInterval(int index0, int index1) { super.setSelectionInterval(-1, -1); }
        });

        JScrollPane scroll = new JScrollPane(playersList);
        scroll.setBorder(new LineBorder(BORDER_COLOR, 1));

        sidebar.add(sideTitle, BorderLayout.NORTH);
        sidebar.add(scroll, BorderLayout.CENTER);

        return sidebar;
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

        JLabel logTitle = new JLabel("REGISTRO DI GIOCO: ");
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

        handPanel = new JPanel(new GridBagLayout());
        handPanel.setBackground(BG_DARK);

        JScrollPane handScroll = new JScrollPane(handPanel);
        handScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        handScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        handScroll.setBorder(null);
        handScroll.getViewport().setBackground(BG_DARK);
        handScroll.setPreferredSize(new Dimension(0, 185));
        handScroll.getHorizontalScrollBar().setUnitIncrement(16);

        JPanel handWrapper = new JPanel(new BorderLayout());
        handWrapper.setBackground(BG_DARK);
        handWrapper.add(handTitle, BorderLayout.NORTH);
        handWrapper.add(handScroll, BorderLayout.CENTER);

        // Bottoni azione
        actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        actionPanel.setBackground(BG_DARK);
        JButton drawBtn    = buildButton();
        drawBtn.addActionListener(e    -> inputQueue.offer("DRAW"));
        actionPanel.add(drawBtn);
        setActionsEnabled(false);

        panel.add(handWrapper, BorderLayout.CENTER);
        panel.add(actionPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JButton buildButton() {
        JButton btn = new JButton("PESCA") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isPressed()  ? GameView.ACCENT_GREEN.darker().darker() :
                        getModel().isRollover() ? GameView.ACCENT_GREEN.darker() : BG_CARD;
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                g2.setColor(isEnabled() ? GameView.ACCENT_GREEN : TEXT_MUTED);
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 10, 10));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(FONT_BUTTON);
        btn.setForeground(GameView.ACCENT_GREEN);
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
                g2.setStroke(new BasicStroke(2.0f));
                g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 12, 12));
                g2.dispose();
            }
        };

        card.setOpaque(false);
        card.setPreferredSize(new Dimension(110, 170));
        card.setBorder(new EmptyBorder(4, 4, 4, 4));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        //Mouse listener per mostrare tooltip con descrizione quando si fa hover su carta
        String description = CARD_DESCRIPTIONS.getOrDefault(cardType.trim(), "Carta sconosciuta.");
        card.addMouseListener(new MouseAdapter() {
            private Popup tooltip;

            @Override
            public void mouseEntered(MouseEvent e) {
                JLabel tip = new JLabel("<html><body style='width:160px;padding:6px'>"
                        + "<b>" + cardName(cardType) + "</b><br>" + description
                        + "</body></html>");
                tip.setBackground(BG_PANEL);
                tip.setForeground(TEXT_PRIMARY);
                tip.setFont(FONT_LABEL);
                tip.setOpaque(true);
                tip.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

                Point loc = card.getLocationOnScreen();
                tooltip = PopupFactory.getSharedInstance().getPopup(
                        card, tip, loc.x + 115, loc.y);
                tooltip.show();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (tooltip != null) { tooltip.hide(); tooltip = null; }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (tooltip != null) { tooltip.hide(); tooltip = null; }
            }
        });

        // Logica click su immagine carte
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (actionPanel.getComponent(0).isEnabled()) {
                    handleCardClick(cardType);
                }
            }
        });

        if (scaledImg != null) {
            JLabel imgLabel = new JLabel(new ImageIcon(scaledImg));
            imgLabel.setHorizontalAlignment(SwingConstants.CENTER);
            card.add(imgLabel, BorderLayout.CENTER);
        }

        JLabel nameLabel = new JLabel("<html><center>" + cardName(cardType) + "</center></html>", SwingConstants.CENTER);
        nameLabel.setFont(FONT_CARD_SM);
        nameLabel.setForeground(accent);
        card.add(nameLabel, BorderLayout.SOUTH);

        return card;
    }

    private void handleCardClick(String cardType) {
        String type = cardType.trim();
        switch (type) {
            case "SKIP"           -> inputQueue.offer("PLAY:SKIP");
            case "ATTACK"         -> inputQueue.offer("PLAY:ATTACK");
            case "SHUFFLE"        -> inputQueue.offer("PLAY:SHUFFLE");
            case "SEE_THE_FUTURE" -> inputQueue.offer("PLAY:SEE_THE_FUTURE");
            case "CAT_CARD"       -> askCatCardTarget();
            case "DEFUSE"         -> appendLog("[Info] Il Defuse si attiva automaticamente se peschi un Kitten!");
            default -> appendLog("[Info] Questa carta non può essere giocata direttamente.");
        }
    }

    /**
     * Aggiorna la lista dei giocatori attivi nella barra laterale.
     * @param playerNames nicknames dei giocatori attivi.
     */
    public void updatePlayersList(List<String> playerNames) {
        SwingUtilities.invokeLater(() -> {
            if (playersListModel != null) {
                playersListModel.clear();
                for (String name : playerNames) {
                    playersListModel.addElement(" • " + name);
                }
            }
        });
    }

    /**
     * Stampa stringa di benvenuti al giocatore con il nickname passato in input.
     * @param nickname del giocatore appena unito alla partita.
     */
    public void showWelcome(String nickname) {
        SwingUtilities.invokeLater(() -> {
            nicknameLabel.setText("Giocatore: " + nickname);
            appendLog("Benvenuto, " + nickname + "!");
        });
    }

    /**
     * Informa il giocatore che il sistema è in attesa in altri giocatori per poter iniziare la partita.
     */
    public void showWaitingForPlayers() {
        SwingUtilities.invokeLater(() -> {
            turnLabel.setText("In attesa dei giocatori...");
            turnLabel.setForeground(TEXT_MUTED);
            appendLog("[Sistema] In attesa degli altri giocatori...");
        });
    }


    /**
     * Mostra popup di fine partita contenente il nome del vincitore.
     * @param winnerNickname vincitore della partita.
     */
    public void showGameOver(String winnerNickname) {
            SwingUtilities.invokeLater(() -> {
                appendLog("\n[FINE] Vince: " + winnerNickname + "!");
                turnLabel.setText("Vince: " + winnerNickname);
                turnLabel.setForeground(ACCENT_GREEN);
                setActionsEnabled(false);

                JPanel panel = new JPanel(new BorderLayout(10, 10));
                panel.setBackground(BG_PANEL);

                Image kittenImg = cardImages.get("WINNER");
                if (kittenImg != null) {
                    Image scaled = kittenImg.getScaledInstance(100, 130, Image.SCALE_SMOOTH);
                    JLabel imgLabel = new JLabel(new ImageIcon(scaled));
                    imgLabel.setBorder(BorderFactory.createLineBorder(ACCENT_GREEN, 2));
                    panel.add(imgLabel, BorderLayout.WEST);
                }

                JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 6));
                textPanel.setBackground(BG_PANEL);

                JLabel titleLabel = new JLabel(winnerNickname + " HA VINTO!");
                titleLabel.setFont(new Font("Georgia", Font.BOLD, 14));
                titleLabel.setForeground(ACCENT_GREEN);

                JLabel msgLabel = new JLabel("Partita terminata.");
                msgLabel.setFont(FONT_LABEL);
                msgLabel.setForeground(TEXT_PRIMARY);

                textPanel.add(titleLabel);
                textPanel.add(msgLabel);
                panel.add(textPanel, BorderLayout.CENTER);

                UIManager.put("OptionPane.background", BG_PANEL);
                UIManager.put("Panel.background", BG_PANEL);

                JOptionPane.showMessageDialog(frame, panel,
                        "Vincitore", JOptionPane.PLAIN_MESSAGE);
                frame.dispose();
            });

    }

    /**
     * Aggiorna la label in alto a destra che indica quando è il nostro turno.
     */
    public void showYourTurn() {
        SwingUtilities.invokeLater(() -> {
            turnLabel.setText("E' il tuo turno!");
            turnLabel.setForeground(ACCENT_GREEN);
            appendLog("\n E' il tuo turno!");

            isPlayerTurn = true;
            setActionsEnabled(isPlayerTurn);
        });
    }

    /**
     * Aggiorna la label in alto a destra che indica il giocatore di turno.
     */
    public void showOtherPlayerTurn(String nickname) {
        SwingUtilities.invokeLater(() -> {
            turnLabel.setText("Turno di: " + nickname);
            turnLabel.setForeground(ACCENT_BLUE);
            appendLog(" Sta giocando: " + nickname);
            isPlayerTurn = false;
            setActionsEnabled(isPlayerTurn);
        });
    }

    /**
     * Mostra la mano del giocatore.
     * @param cardNames nomi delle carte in mano al gicatore.
     */
    public void showHand(List<String> cardNames) {
        SwingUtilities.invokeLater(() -> {
            handPanel.removeAll();
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridy = 0;
            gbc.insets = new Insets(0, 4, 0, 4);
            gbc.anchor = GridBagConstraints.CENTER;
            for (String card : cardNames) {
                if (!card.isBlank()) {
                    handPanel.add(buildCardPanel(card.trim()), gbc);
                }
            }
            handPanel.revalidate();
            handPanel.repaint();
            setActionsEnabled(isPlayerTurn); //testare
        });
    }

    /**
     * Stampa la carta appena pescata.
     * @param cardType tipo della pescata.
     */
    public void showCardDrawn(String cardType) {
        SwingUtilities.invokeLater(() -> {
            appendLog("[Pesca] Hai pescato: " + cardName(cardType));
            setActionsEnabled(false);
        });
    }

    /**
     * Mostra la carta giocata.
     * @param nickname del giocatore che ha giocato la carta.
     * @param cardType tipo della carta giocata.
     */
    public void showCardPlayed(String nickname, String cardType) {
        SwingUtilities.invokeLater(() ->
                appendLog("[Giocata] " + nickname + " ha giocato: " + cardName(cardType)));
    }

    /**
     * Informa il giocatore che gli è stata rubata una carta.
     * @param cardType tipo della carta rubata.
     */
    public void showStolenCard(String cardType) {
        SwingUtilities.invokeLater(() ->
                appendLog("[Furto] Ti e' stata rubata: " + cardName(cardType)));
    }

    /**
     * Mostra le prime tre carte in cima al mazzo quando viene utilizzata una carta See the future.
     * @param top3 lista delle 3 carte in cima al mazzo.
     */
    public void showSeeTheFuture(List<String> top3) {
        SwingUtilities.invokeLater(() -> {
            appendLog("[See the Future] Prossime 3 carte del mazzo:");
            for (int i = 0; i < top3.size(); i++) {
                String c = top3.get(i).trim();
                if (!c.isEmpty()) appendLog("   " + (i + 1) + ". " + cardName(c));
            }
        });
    }

    /**
     * Informa il giocatore che ha pescato un exploding kitten.
     */
    public void showExplosion() {
        SwingUtilities.invokeLater(() -> {
            appendLog("\n HAI PESCATO UN EXPLODING KITTEN!!! \n");
            turnLabel.setText("!!! EXPLODING KITTEN !!!");
            turnLabel.setForeground(ACCENT_RED);
        });
    }

    /**
     * Informa il giocatore che è stato utilizzato automaticamento il Defuse che aveva in mano.
     */
    public void showDefuseUsed() {
        SwingUtilities.invokeLater(() ->
                appendLog("[Defuse] Hai usato il Defuse. Sei salvo!"));
    }


    /**
     * Mostra popup di eliminazione dopo che è stato pescato un Exploding Kitten e il giocatore non aveva carte Defuse.
     */
    public void showYouAreEliminated() {
        SwingUtilities.invokeLater(() -> {
            appendLog("\n Sei stato eliminato! Nessun Defuse disponibile.\n");
            turnLabel.setText("Sei eliminato");
            turnLabel.setForeground(ACCENT_RED);
            setActionsEnabled(false);

            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBackground(BG_PANEL);

            Image kittenImg = cardImages.get("EXPLODING_KITTEN");
            if (kittenImg != null) {
                Image scaled = kittenImg.getScaledInstance(100, 130, Image.SCALE_SMOOTH);
                JLabel imgLabel = new JLabel(new ImageIcon(scaled));
                imgLabel.setBorder(BorderFactory.createLineBorder(ACCENT_RED, 2));
                panel.add(imgLabel, BorderLayout.WEST);
            }

            JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 6));
            textPanel.setBackground(BG_PANEL);

            JLabel titleLabel = new JLabel("SEI STATO ELIMINATO!");
            titleLabel.setFont(new Font("Georgia", Font.BOLD, 14));
            titleLabel.setForeground(ACCENT_RED);

            JLabel msgLabel = new JLabel("Nessun Defuse disponibile.");
            msgLabel.setFont(FONT_LABEL);
            msgLabel.setForeground(TEXT_PRIMARY);

            textPanel.add(titleLabel);
            textPanel.add(msgLabel);
            panel.add(textPanel, BorderLayout.CENTER);

            UIManager.put("OptionPane.background", BG_PANEL);
            UIManager.put("Panel.background", BG_PANEL);

            JOptionPane.showMessageDialog(frame, panel,
                    "Eliminato", JOptionPane.PLAIN_MESSAGE);
            frame.dispose();
        });
    }

    /**
     * Mostra un messaggio di errore.
     * @param message
     */
    public void showError(String message) {
        SwingUtilities.invokeLater(() -> appendLog("[Errore] " + message));
    }

    /**
     * Avvisa il giocatore quando non è il suo turno e sta tentando di giocare comunque.
     */
    public void showNotYourTurn() {
        SwingUtilities.invokeLater(() -> appendLog("Errore: Non e' il tuo turno!"));
    }

    //TODO Rimuovere, non dovrebbe più servire dopo refactor view
    public void showCardNotInHand() {
        SwingUtilities.invokeLater(() -> appendLog("Errore: Non hai quella carta in mano."));
    }

    /**
     * Avvisa il giocatore che il mazzo è stato mischiato.
     */
    public void showShuffled() {
        SwingUtilities.invokeLater(() -> appendLog("Shuffle: Il mazzo e' stato mischiato."));
    }

    public String askAction() {
        SwingUtilities.invokeLater(() -> setActionsEnabled(isPlayerTurn));
        try {
            return inputQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "DRAW";
        }
    }

    /**
     * Mostra popup quando viene pescato un Exploding kitten e il giocatore possiede un Defuse.
     * @param deckSize dimensione del mazzo.
     * @return la posizione in cui inserire l'exploding kitten.
     */

    public int askDefusePosition(int deckSize) {
        inputQueue.clear();
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG_PANEL);

        Image kittenImg = cardImages.get("EXPLODING_KITTEN");
        if (kittenImg != null) {
            Image scaled = kittenImg.getScaledInstance(100, 130, Image.SCALE_SMOOTH);
            JLabel imgLabel = new JLabel(new ImageIcon(scaled));
            imgLabel.setBorder(BorderFactory.createLineBorder(ACCENT_RED, 2));
            panel.add(imgLabel, BorderLayout.WEST);
        }

        SpinnerNumberModel model = new SpinnerNumberModel(0, 0, deckSize, 1);
        JSpinner spinner = new JSpinner(model);

        JComponent editor = spinner.getEditor();
        JFormattedTextField textField = ((JSpinner.DefaultEditor) editor).getTextField();
        textField.setBackground(BG_CARD);
        textField.setForeground(TEXT_PRIMARY);
        textField.setCaretColor(ACCENT_ORANGE);
        textField.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        spinner.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        JPanel rightPanel = new JPanel(new BorderLayout(6, 6));
        rightPanel.setBackground(BG_PANEL);

        JLabel titleLabel = new JLabel("!!! HAI PESCATO UN EXPLODING KITTEN !!!");
        titleLabel.setFont(new Font("Georgia", Font.BOLD, 13));
        titleLabel.setForeground(ACCENT_RED);

        JLabel msgLabel = new JLabel("In che posizione vuoi reinserire il Kitten?");
        msgLabel.setFont(FONT_LABEL);
        msgLabel.setForeground(TEXT_PRIMARY);

        JLabel hintLabel = new JLabel("(Range consentito: 0 - " + deckSize + ")");
        hintLabel.setFont(new Font("Georgia", Font.PLAIN, 11));
        hintLabel.setForeground(TEXT_MUTED);

        JPanel textPanel = new JPanel(new GridLayout(3, 1, 0, 4));
        textPanel.setBackground(BG_PANEL);
        textPanel.add(titleLabel);
        textPanel.add(msgLabel);
        textPanel.add(hintLabel);

        rightPanel.add(textPanel, BorderLayout.NORTH);
        rightPanel.add(spinner, BorderLayout.CENTER);
        panel.add(rightPanel, BorderLayout.CENTER);

        SwingUtilities.invokeLater(() -> {
            UIManager.put("OptionPane.background", BG_PANEL);
            UIManager.put("Panel.background", BG_PANEL);

            int result = JOptionPane.showConfirmDialog(
                    frame, panel,
                    "Defuse in azione!",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
            );

            int selectedPos = (result == JOptionPane.OK_OPTION) ? (int) spinner.getValue() : 0;
            inputQueue.offer("DEFUSE:" + selectedPos);
        });

        try {
            String raw = inputQueue.take();
            return Math.max(0, Integer.parseInt(raw.replace("DEFUSE:", "").trim()));
        } catch (Exception e) {
            return 0;
        }
    }



    /**
     * Metodo per mostrare nel log che un player si è disconnesso e rimozione dalla lista dei players.
     * @param nickname del player disconnesso.
     */
    public void showPlayerDisconnected(String nickname) {
        SwingUtilities.invokeLater(() -> {
            appendLog("[DISCONNESSIONE] " + nickname + " si è disconnesso.");
            removePlayerFromList(nickname);
        });
    }

    /**
     * Meotodo per rimuovere un player dalla lista quando si disconnette o viene eliminato.
     * @param nickname
     */
    public void removePlayerFromList(String nickname){
        for (int i = 0; i < playersListModel.size(); i++) {
            String name = playersListModel.get(i).replace(" • ", "");
            if (name.equals(nickname)) {
                playersListModel.remove(i);
                break;
            }
        }
    }

    private void askCatCardTarget() {
        java.util.List<String> validPlayers = new java.util.ArrayList<>();
        String myName = nicknameLabel.getText().replace("Giocatore: ", "").trim();


        for (int i = 0; i < playersListModel.size(); i++) {
            String playerName = playersListModel.getElementAt(i);
            if (playerName.startsWith(" • ")) {playerName = playerName.substring(3);}
            if (!playerName.equals(myName)) { validPlayers.add(playerName);}
        }

        if (validPlayers.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Nessun bersaglio disponibile!", "Cat Card", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String[] options = validPlayers.toArray(new String[0]);
        String target = (String) JOptionPane.showInputDialog(
                frame,
                "Seleziona il giocatore da cui vuoi rubare:",
                "Cat Card",
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

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

    public void showDefuseUsedByPlayer(String nickname) {
        SwingUtilities.invokeLater(() -> {
            appendLog( "[DEFUSED]"+ nickname+ " ha usato un Defuse per salvarsi!");
            removePlayerFromList(nickname);
        });
    }
}
