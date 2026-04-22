package explodingkittens.game.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * View utilizzata per far unire i giocatori alla partita.
 */
public class SetupView {
    private static final Color BG_DARK       = new Color(18, 18, 28);
    private static final Color BG_PANEL      = new Color(28, 28, 42);
    private static final Color ACCENT_ORANGE = new Color(255, 120, 40);
    private static final Color TEXT_PRIMARY  = new Color(240, 235, 220);
    private JFrame frame;
    private JTextField nameField;
    private JSpinner playerSpinner;
    private JButton startButton;
    private JLabel errorLabel;
    private JLabel countLabel;

    public SetupView() {
        buildUI();
    }

    private void buildUI() {
        frame = new JFrame("Exploding Kittens - Setup");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 500);
        frame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(BG_DARK);
        mainPanel.setBorder(new EmptyBorder(30, 30, 30, 30));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 0, 10, 0);
        gbc.gridx = 0;

        //Titolo
        JLabel titleLabel = new JLabel("EXPLODING KITTENS", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Georgia", Font.BOLD, 28));
        titleLabel.setForeground(ACCENT_ORANGE);
        gbc.gridy = 0;
        mainPanel.add(titleLabel, gbc);

        //Campo per inserire il nickname
        JLabel nameLabel = new JLabel("IL TUO NICKNAME:");
        nameLabel.setForeground(TEXT_PRIMARY);
        gbc.gridy = 1;
        mainPanel.add(nameLabel, gbc);
        nameField = new JTextField("Giocatore 1");
        styleTextField(nameField);
        gbc.gridy = 2;
        mainPanel.add(nameField, gbc);

        //Label per mostrare eventuali errori nella scelta del nickmname
        errorLabel = new JLabel("");
        errorLabel.setForeground(new Color(220, 60, 60));
        errorLabel.setFont(new Font("Georgia", Font.PLAIN, 12));
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 3;
        gbc.insets = new Insets(0, 0, 5, 0);
        mainPanel.add(errorLabel, gbc);


        //Scelta numero giocatori
        countLabel = new JLabel("NUMERO GIOCATORI (2-4):");
        countLabel.setForeground(TEXT_PRIMARY);
        gbc.gridy = 4;
        mainPanel.add(countLabel, gbc);
        SpinnerModel model = new SpinnerNumberModel(2, 2, 4, 1);
        playerSpinner = new JSpinner(model);
        styleSpinner(playerSpinner);
        gbc.gridy = 5;
        mainPanel.add(playerSpinner, gbc);


        // Bottone per entrare in partita
        startButton = new JButton("CONFERMA");
        styleButton(startButton);
        gbc.gridy = 6 ;
        gbc.insets = new Insets(30, 0, 10, 0);
        mainPanel.add(startButton, gbc);

        frame.add(mainPanel);
        frame.setVisible(true);
    }

    public void setAsHost(boolean isHost) {
        SwingUtilities.invokeLater(() -> {
            countLabel.setVisible(isHost);
            playerSpinner.setVisible(isHost);
            frame.revalidate();
            frame.repaint();
            frame.pack();
        });
    }

    private void styleTextField(JTextField field) {
        field.setBackground(BG_PANEL);
        field.setForeground(Color.WHITE);
        field.setCaretColor(ACCENT_ORANGE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT_ORANGE, 1),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        field.setFont(new Font("SansSerif", Font.PLAIN, 16));
    }

    private void styleSpinner(JSpinner spinner) {
        spinner.getEditor().getComponent(0).setBackground(BG_PANEL);
        spinner.getEditor().getComponent(0).setForeground(Color.WHITE);
        spinner.setBorder(BorderFactory.createLineBorder(ACCENT_ORANGE, 1));
    }

    private void styleButton(JButton btn) {
        btn.setFont(new Font("Georgia", Font.BOLD, 16));
        btn.setForeground(Color.WHITE);
        btn.setBackground(ACCENT_ORANGE);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(12, 0, 12, 0));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    public String getPlayerName() { return nameField.getText().trim(); }
    public int getPlayerCount() {
        return (int) playerSpinner.getValue() ;
    }
    public void addStartListener(java.awt.event.ActionListener al) { startButton.addActionListener(al); }
    public void close() { frame.dispose(); }
    public void showNicknameError(String message) {
        SwingUtilities.invokeLater(() -> {
            errorLabel.setText(message);
            nameField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(220, 60, 60), 2),
                    BorderFactory.createEmptyBorder(5, 10, 5, 10)));
            startButton.setEnabled(true);
        });
    }
    public void updateButtonText(String text) {
        startButton.setText(text);
    }

    public void removeListeners() {
        for (java.awt.event.ActionListener al : startButton.getActionListeners()) {
            startButton.removeActionListener(al);
        }
    }
    public void setStartButtonEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> startButton.setEnabled(enabled));
    }
}