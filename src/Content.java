import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.*;


// ... (Importe bleiben gleich)

public class Content {

    // Hilfsklasse für abgerundete Panels mit optionalem Rand
    static class RoundedPanel extends JPanel {
        private int radius;
        private Color borderColor;

        public RoundedPanel(int radius, Color borderColor) {
            this.radius = radius;
            this.borderColor = borderColor;
            setOpaque(false);
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

    public static JPanel create(MinecraftAuth.LoginData loginData) throws IOException, FontFormatException {
        Color darkGrayBackground = new Color(43, 43, 43);
        Color Cliont = new Color(188, 19, 254);
        Color lightBorder = new Color(100, 100, 100); // Hellerer Strich

        InputStream fontStream = Content.class.getResourceAsStream("/Minecraft.ttf");
        Font mcFont = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(24f);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(darkGrayBackground);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // ===== Top Panel: Startbereich (Radius 30, mit Rand) =====
        RoundedPanel startPanel = new RoundedPanel(30, lightBorder);
        startPanel.setLayout(new BoxLayout(startPanel, BoxLayout.Y_AXIS));
        startPanel.setBackground(new Color(60, 60, 60));
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
        versionBox.setMaximumSize(new Dimension(250, 35));
        versionBox.setPreferredSize(new Dimension(250, 35));
        versionBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        versionBox.setBackground(new Color(70, 70, 70)); // Hintergrundfarbe der Box
        versionBox.setForeground(Color.WHITE);
        versionBox.setFont(mcFont.deriveFont(16f));

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
                g2.setColor(Color.WHITE);
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
        startButton.setBackground(Cliont);
        Dimension buttonSize = new Dimension(300, 100);
        startButton.setMaximumSize(buttonSize);
        startButton.setPreferredSize(buttonSize); // Wichtig, damit Swing die Größe erzwingt
        startButton.setMinimumSize(buttonSize);

        startButton.addActionListener(e -> {
            try {
                Main.setAppBusy(true);
                MinecraftLauncher.setVersion(versionBox.getSelectedItem().toString());
                Process process = MinecraftLauncher.launch(loginData);

                new Thread(() -> {
                    try {
                        process.waitFor();
                    } catch (InterruptedException ignored) {
                    } finally {
                        Main.setAppBusy(false);
                    }
                }).start();
            } catch (Exception ex) {
                Main.setAppBusy(false);
                ex.printStackTrace();
            }
        });

        startPanel.add(startButton);

        // ===== Mitte: Abstandshalter (Glue) =====
        // Dies drückt das News-Panel nach ganz unten
        mainPanel.add(startPanel);
        mainPanel.add(Box.createVerticalGlue());
        mainPanel.add(Box.createVerticalStrut(20)); // Kleiner fixer Abstand

        // ===== Bottom Panel: News (Radius 20, mit Rand) =====
        RoundedPanel newsPanel = new RoundedPanel(20, lightBorder);
        newsPanel.setLayout(new BoxLayout(newsPanel, BoxLayout.Y_AXIS));
        newsPanel.setBackground(new Color(55, 55, 55));
        newsPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        newsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120)); // Feste Höhe unten

        JLabel newsTitle = new JLabel("NEWS");
        newsTitle.setFont(mcFont.deriveFont(18f));
        newsTitle.setForeground(Color.WHITE);
        newsTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel emptyNews = new JLabel("Keine News verfügbar");
        emptyNews.setForeground(Color.LIGHT_GRAY);
        emptyNews.setFont(mcFont.deriveFont(16f));
        emptyNews.setAlignmentX(Component.CENTER_ALIGNMENT);

        newsPanel.add(newsTitle);
        newsPanel.add(Box.createVerticalGlue());
        newsPanel.add(emptyNews);
        newsPanel.add(Box.createVerticalGlue());

        mainPanel.add(newsPanel);

        // ScrollPane & Wrapper (wie zuvor)
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(darkGrayBackground);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(scrollPane, BorderLayout.CENTER);
        wrapper.setBackground(darkGrayBackground);

        return wrapper;
    }
}
