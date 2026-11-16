import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


// ... (Importe bleiben gleich)

public class Content {

    // Statische Variable zum Speichern der versionBox für späteren Zugriff
    private static JComboBox<String> versionBox;
    private static final Color Cliont = Main.cliont;

    public static JPanel create() throws IOException, FontFormatException {
        Color darkGrayBackground = UIUtils.BG;
        Color lightBorder = UIUtils.LIGHT_BORDER; // Hellerer Strich

        InputStream fontStream = Content.class.getResourceAsStream("/Minecraft.ttf");
        Font mcFont = new Font("SansSerif", Font.PLAIN, 24);
        if (fontStream != null) {
            try {
                mcFont = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(24f);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(darkGrayBackground);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // ===== Top Panel: Startbereich (Radius 30, mit Rand) =====
        RoundedPanel startPanel = new RoundedPanel(30, lightBorder);
        startPanel.setLayout(new BoxLayout(startPanel, BoxLayout.Y_AXIS));
        startPanel.setBackground(UIUtils.CARD);
        startPanel.setBorder(BorderFactory.createEmptyBorder(40, 60, 40, 60));

        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        // === Versionen aus .minecraft/versions laden ===
        Path versionsDir = Paths.get(System.getProperty("user.home"), "AppData", "Roaming", ".cliont", "profiles");

        if (Files.exists(versionsDir) && Files.isDirectory(versionsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionsDir)) {
                for (Path path : stream) {
                    if (Files.isDirectory(path)) {
                        model.addElement(path.getFileName().toString());
                    }
                }
            }
        } else {
            model.addElement("No versions found");
        }

        JComboBox<String> versionBox = new JComboBox<>(model);
        Content.versionBox = versionBox; // Speichere für späteren Zugriff
        versionBox.setMaximumSize(new Dimension(250, 35));
        versionBox.setPreferredSize(new Dimension(250, 35));
        versionBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        versionBox.setBackground(UIUtils.INPUT); // Hintergrundfarbe der Box
        versionBox.setForeground(UIUtils.TEXT);
        //versionBox.setFont(mcFont.deriveFont(16f));

// --- Pfeil entfernen und Ecken abrunden ---
        versionBox.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                // Gibt einen unsichtbaren Button zurück, um den Pfeil zu entfernen
                JButton button = new JButton();
                button.setBorder(BorderFactory.createEmptyBorder());
                button.setVisible(false);
                return button;
            }

            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c.getBackground());
                // Hier den Radius anpassen (z.B. 10)
                g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 10, 10);
                g2.dispose();
                super.paint(g, c);
            }
        });

// Entfernt den Standard-Rahmen, der die Rundung stören könnte


        startPanel.add(versionBox);
// ...

        startPanel.add(Box.createVerticalStrut(40));

        JLabel loginLabel = new JLabel("Please log in to start the game.");
        loginLabel.setForeground(UIUtils.TEXT_MUTED);
        loginLabel.setFont(mcFont.deriveFont(16f));
        loginLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        startPanel.add(loginLabel);

        Timer loginCheckTimer = new Timer(500, e -> {
            loginLabel.setVisible(Main.loginData == null);
            startPanel.revalidate();
            startPanel.repaint();
        });

        loginCheckTimer.start();

        // Play Button (bleibt rund wie zuvor)
        JButton startButton = new JButton("Play") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Farbe ändern, wenn gedrückt
                Color bgColor = getModel().isPressed() ? Color.DARK_GRAY : getBackground();
                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2.setColor(UIUtils.TEXT);
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent()) / 2 - 4;
                g2.setFont(getFont());
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };

        startButton.setFont(mcFont.deriveFont(32f));
        startButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        startButton.setContentAreaFilled(false);
        startButton.setBorderPainted(false);
        startButton.setOpaque(false);
        startButton.setBackground(UIUtils.alpha(Cliont, 150));
        Dimension buttonSize = new Dimension(300, 100);
        startButton.setMaximumSize(buttonSize);
        startButton.setPreferredSize(buttonSize); // Wichtig, damit Swing die Größe erzwingt
        startButton.setMinimumSize(buttonSize);
        startButton.setEnabled(true);

        startButton.addActionListener(e -> {
            try {
                String selectedProfile = versionBox.getSelectedItem() != null ? versionBox.getSelectedItem().toString() : "";
                if (selectedProfile.isEmpty() || Main.isProfileRunning(selectedProfile)) {
                    return;
                }
                startButton.setEnabled(false);
                MinecraftLauncher.setVersion(selectedProfile);
                System.out.println(Main.loginData);
                Process process = Main.launchProfile(selectedProfile, () -> MinecraftLauncher.launch(Main.loginData));
                Main.setRunningProfile(selectedProfile, process);

                new Thread(() -> {
                    try {
                        process.waitFor();
                    } catch (InterruptedException ignored) {
                    } finally {
                        startButton.setEnabled(true);
                    }
                }).start();
            } catch (Exception ex) {
                startButton.setEnabled(true);
                ex.printStackTrace();
            }
        });

        startPanel.add(startButton);

        Timer loginButtonCheckTimer = new Timer(500, e -> {
            String selectedProfile = versionBox.getSelectedItem() != null ? versionBox.getSelectedItem().toString() : "";
            Main.configureProfileButtonState(startButton, selectedProfile, false);
        });

        loginButtonCheckTimer.start();


        // ===== Mitte: Abstandshalter (Glue) =====
        // Dies drückt das News-Panel nach ganz unten
        mainPanel.add(startPanel);
        mainPanel.add(Box.createVerticalGlue());
        mainPanel.add(Box.createVerticalStrut(20)); // Kleiner fixer Abstand

        // ===== Bottom Panel: News (Radius 20, mit Rand) =====
        RoundedPanel newsPanel = new RoundedPanel(20, lightBorder);
        newsPanel.setLayout(new BorderLayout(10, 10));
        newsPanel.setBackground(UIUtils.CARD);
        newsPanel.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        newsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));

        JPanel newsHeader = new JPanel(new BorderLayout());
        newsHeader.setOpaque(false);

        JLabel newsTitle = new JLabel("NEWS");
        newsTitle.setFont(mcFont.deriveFont(18f));
        newsTitle.setForeground(UIUtils.TEXT);

        JLabel liveBadge = new JLabel("LIVE");
        liveBadge.setOpaque(true);
        liveBadge.setBackground(UIUtils.alpha(Cliont, 28));
        liveBadge.setForeground(Cliont);
        liveBadge.setFont(mcFont.deriveFont(10f).deriveFont(Font.BOLD));
        liveBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIUtils.alpha(Cliont, 80), 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        liveBadge.setHorizontalAlignment(SwingConstants.CENTER);

        newsHeader.add(newsTitle, BorderLayout.WEST);
        newsHeader.add(liveBadge, BorderLayout.EAST);
        newsPanel.add(newsHeader, BorderLayout.NORTH);

        JPanel newsList = new JPanel();
        newsList.setLayout(new GridLayout(2, 3, 10, 10));
        newsList.setOpaque(false);

        String[][] newsItems = new String[][] {
                {"Lilac Glow update", "Fresh purple UI polish and smoother launch flows."},
                {"Profile sync", "Your custom Minecraft profiles now stay neatly organized."},
                {"Faster install flow", "Mod setup and launcher checks are quicker than before."},
                {"Forge ready", "Compatibility checks for modded setups are running cleaner."},
                {"Launcher stability", "Fewer crashes while switching between profiles and versions."},
                {"Quick play", "Launch your saved version with one click from the dashboard."}
        };

        for (String[] item : newsItems) {
            RoundedPanel newsBlock = new RoundedPanel(16, UIUtils.alpha(Cliont, 55));
            newsBlock.setLayout(new BorderLayout(10, 0));
            newsBlock.setBackground(UIUtils.alpha(Cliont, 18));
            newsBlock.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
            newsBlock.setPreferredSize(new Dimension(0, 150));

            JLabel dot = new JLabel("•");
            dot.setForeground(Cliont);
            dot.setFont(new Font("SansSerif", Font.BOLD, 22));
            dot.setHorizontalAlignment(SwingConstants.CENTER);
            dot.setPreferredSize(new Dimension(18, 18));

            JPanel textPanel = new JPanel();
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.setOpaque(false);

            JLabel title = new JLabel(item[0]);
            title.setForeground(UIUtils.TEXT);
            title.setFont(mcFont.deriveFont(14f));
            title.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel subtitle = new JLabel(item[1]);
            subtitle.setForeground(UIUtils.TEXT_MUTED);
            subtitle.setFont(mcFont.deriveFont(11f));
            subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

            textPanel.add(title);
            textPanel.add(Box.createVerticalStrut(2));
            textPanel.add(subtitle);

            newsBlock.add(dot, BorderLayout.WEST);
            newsBlock.add(textPanel, BorderLayout.CENTER);
            newsList.add(newsBlock);
        }

        newsPanel.add(newsList, BorderLayout.CENTER);
        mainPanel.add(newsPanel);

        // ScrollPane & Wrapper (wie zuvor)
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        UIUtils.styleScrollPane(scrollPane);
        scrollPane.getViewport().setBackground(darkGrayBackground);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(scrollPane, BorderLayout.CENTER);
        wrapper.setBackground(darkGrayBackground);

        return wrapper;
    }

    /**
     * Aktualisiert die Profil-Dropdown-Liste mit den neuesten Profilen aus dem Verzeichnis
     */
    public static void refreshProfileList() {
        if (versionBox == null) {
            return; // Dropdown-Liste wurde noch nicht initialisiert
        }

        SwingUtilities.invokeLater(() -> {
            try {
                DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) versionBox.getModel();
                String currentlySelected = versionBox.getSelectedItem() != null ? versionBox.getSelectedItem().toString() : null;
                model.removeAllElements();

                Path versionsDir = Paths.get(System.getProperty("user.home"), "AppData", "Roaming", ".cliont", "profiles");

                if (Files.exists(versionsDir) && Files.isDirectory(versionsDir)) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionsDir)) {
                        for (Path path : stream) {
                            if (Files.isDirectory(path)) {
                                model.addElement(path.getFileName().toString());
                            }
                        }
                    }
                } else {
                    model.addElement("No versions found");
                }

                if (model.getSize() > 0) {
                    if (currentlySelected != null) {
                        for (int i = 0; i < model.getSize(); i++) {
                            if (currentlySelected.equals(model.getElementAt(i))) {
                                versionBox.setSelectedIndex(i);
                                return;
                            }
                        }
                    }
                    versionBox.setSelectedIndex(0);
                }
            } catch (IOException e) {
                System.err.println("Fehler beim Aktualisieren der Profil-Liste: " + e.getMessage());
            }
        });
    }

    public static void selectProfile(String profileName) {
        if (versionBox == null || profileName == null || profileName.isBlank()) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            ComboBoxModel<String> model = versionBox.getModel();
            for (int i = 0; i < model.getSize(); i++) {
                if (profileName.equals(model.getElementAt(i))) {
                    versionBox.setSelectedIndex(i);
                    return;
                }
            }
            refreshProfileList();
        });
    }

    // Hilfsklasse für abgerundete Panels mit optionalem Rand
    static class RoundedPanel extends JPanel {
        private final int radius;
        private Color borderColor;

        public RoundedPanel(int radius, Color borderColor) {
            this.radius = radius;
            this.borderColor = borderColor;
            setOpaque(false);
        }

        public Color getBorderColor() {
            return borderColor;
        }

        public void setBorderColor(Color borderColor) {
            this.borderColor = borderColor;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Hintergrund zeichnen
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);

            // Hellen Strich/Rahmen zeichnen
            if (borderColor != null) {
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(2)); // Dicke des Strichs
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, radius, radius);
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }
}