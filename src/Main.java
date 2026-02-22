import java.awt.*;
import java.io.InputStream;
import javax.swing.*;
import java.awt.FontFormatException;
import java.awt.Font;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException, FontFormatException {

        JPanel centerPanel = new JPanel(new CardLayout());
        JPanel content = Content.create();
        JPanel version = Version.create();
        JPanel mods = CurseForgeBrowser.create();

        // ===== Farben & Größen =====
        Color sideBarColor = new Color(34, 34, 34);
        Color buttonColor = new Color(89, 89, 89);
        Dimension buttonSize = new Dimension(200, 30);

        // ===== Frame =====
        JFrame frame = new JFrame("Cliont");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 450);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());
        frame.setIconImage(new ImageIcon(Main.class.getResource("Icon.png")).getImage());

        // ===== Font =====
        InputStream fontStream = Main.class.getResourceAsStream("/Minecraft.ttf");
        Font mcFont = Font.createFont(Font.TRUETYPE_FONT, fontStream);

        // ===== Sidebar =====
        JPanel sidebar = new JPanel();
        sidebar.setPreferredSize(new Dimension(200, 0));
        sidebar.setBackground(sideBarColor);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Cliont");
        title.setFont(mcFont.deriveFont(42f));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        sidebar.add(Box.createVerticalStrut(20));
        sidebar.add(title);
        sidebar.add(Box.createVerticalStrut(30));

        JButton homebutton = createSidebarButton("Home", mcFont, buttonColor, buttonSize);
        JButton modsbutton = createSidebarButton("Mods", mcFont, buttonColor, buttonSize);
        JButton versionsbutton = createSidebarButton("Versions", mcFont, buttonColor, buttonSize);

        //button funktionen
        versionsbutton.addActionListener(e -> {
            content.setVisible(false);
            version.setVisible(true);
            mods.setVisible(false);
        });

        homebutton.addActionListener(e -> {
            content.setVisible(true);
            version.setVisible(false);
            mods.setVisible(false);
        });

        modsbutton.addActionListener(e ->{
            content.setVisible(false);
            version.setVisible(false);
            mods.setVisible(true);
        });

        content.setVisible(true);
        version.setVisible(false);
        mods.setVisible(false);

        sidebar.add(homebutton);
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(modsbutton);
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(versionsbutton);

        // ===== Frame füllen =====
        frame.add(sidebar, BorderLayout.WEST);
        centerPanel.add(content);
        centerPanel.add(version, BorderLayout.NORTH);
        centerPanel.add(mods);
        frame.add(centerPanel);

        content.setVisible(true);
        frame.setVisible(true);
    }

    // ===== Button-Hilfsfunktion =====
    private static JButton createSidebarButton(
            String text, Font font, Color bg, Dimension size) {

        JButton button = new JButton(text);
        button.setFont(font.deriveFont(18f));
        button.setMaximumSize(size);
        button.setPreferredSize(size);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setBackground(bg);
        button.setForeground(Color.WHITE);
        return button;
    }
}
