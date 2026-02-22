import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Content {
    public static JPanel create() throws IOException, FontFormatException {
        
        Color darkGrayBackground = new Color(43, 43, 43);

        InputStream fontStream = Content.class.getResourceAsStream("/Minecraft.ttf");
        Font mcFont = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(24f);

        // ===== Add Version Box =====

        Path versionsDir = Paths.get(
                System.getProperty("user.home"),
                "AppData", "Roaming", ".cliont", "versions"
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
        versionBox.setPreferredSize(new Dimension(325, 28));
        versionBox.setMaximumSize(new Dimension(325, 28));
        versionBox.setBackground(Color.white);
        versionBox.setOpaque(false);
        versionBox.addActionListener(e -> {
            String selectedVersion = (String) versionBox.getSelectedItem();
            MinecraftLauncher.setVersion(selectedVersion);
        });

        // ===== Content =====
        JPanel content = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        content.setBackground(darkGrayBackground);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        
        JButton startButton = new JButton("Start");
        startButton.setFont(mcFont.deriveFont(24f));
        startButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        startButton.setFocusPainted(false);
        startButton.setBorderPainted(false);
        startButton.setBackground(Color.WHITE);
        startButton.setMargin(new Insets(60, 130, 60, 130));


        startButton.addActionListener(e -> {
            try {
                MinecraftLauncher.launch();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        content.add(versionBox);
        content.add(startButton);

        return content;
    }
}
