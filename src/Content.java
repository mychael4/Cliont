import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

public class Content {

    public static JPanel create() throws IOException, FontFormatException {

        Color darkGrayBackground = new Color(43, 43, 43);
        Color Cliont = new Color(188, 19, 254);

        // ===== Load Font =====
        InputStream fontStream = Content.class.getResourceAsStream("/Minecraft.ttf");
        Font mcFont = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(24f);

        // ===== Main Panel =====
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(darkGrayBackground);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // ===== Top Panel: Startbereich =====
        JPanel startPanel = new JPanel();
        startPanel.setLayout(new BoxLayout(startPanel, BoxLayout.Y_AXIS));
        startPanel.setBackground(new Color(60, 60, 60));
        startPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 2, true),
                BorderFactory.createEmptyBorder(40, 60, 40, 60)
        ));

        // Version Box
        Path versionsDir = Paths.get(
                System.getProperty("user.home"),
                "AppData", "Roaming", ".cliont", "profiles"
        );

        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        if (Files.exists(versionsDir) && Files.isDirectory(versionsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionsDir)) {
                for (Path p : stream) {
                    if (Files.isDirectory(p)) {
                        model.addElement(p.getFileName().toString());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        JComboBox<String> versionBox = new JComboBox<>(model);
        versionBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        versionBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        versionBox.addActionListener(e -> {
            String selectedVersion = (String) versionBox.getSelectedItem();
            MinecraftLauncher.setVersion(selectedVersion);
        });


        startPanel.add(versionBox);
        startPanel.add(Box.createVerticalStrut(40));

        // Start Button
        JButton startButton = new JButton("Play");
        startButton.setFont(mcFont.deriveFont(32f));
        startButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        startButton.setFocusPainted(false);
        startButton.setBorderPainted(false);
        startButton.setBackground(Cliont);
        startButton.setMargin(new Insets(40, 120, 40, 120));
        startButton.addActionListener(e -> {

            startButton.setBackground(new Color(70,70,70));
            startButton.setEnabled(false);

            new Thread(() -> {
                try {

                    MinecraftLauncher.launch();

                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                SwingUtilities.invokeLater(() -> {
                    startButton.setBackground(Cliont);
                    startButton.setEnabled(true);
                });

            }).start();

        });

        startPanel.add(startButton);

        // ===== Spacer, damit Startpanel fast ganze Fläche ausfüllt =====
        mainPanel.add(startPanel);
        mainPanel.add(Box.createVerticalGlue());

        // ===== Bottom Panel: News =====
        JPanel newsPanel = new JPanel();
        newsPanel.setLayout(new BoxLayout(newsPanel, BoxLayout.Y_AXIS));
        newsPanel.setBackground(new Color(55, 55, 55));
        newsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                "News",
                0, 0,
                mcFont.deriveFont(18f),
                Color.WHITE
        ));
        newsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        JLabel emptyNews = new JLabel("Keine News verfügbar");
        emptyNews.setForeground(Color.LIGHT_GRAY);
        emptyNews.setFont(mcFont.deriveFont(16f));
        emptyNews.setAlignmentX(Component.CENTER_ALIGNMENT);

        newsPanel.add(Box.createVerticalGlue());
        newsPanel.add(emptyNews);
        newsPanel.add(Box.createVerticalGlue());

        mainPanel.add(newsPanel);

        // ===== ScrollPane für das ganze Panel =====
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // ===== Wrapper Panel =====
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(scrollPane, BorderLayout.CENTER);
        wrapper.setBackground(darkGrayBackground);

        return wrapper;
    }
}