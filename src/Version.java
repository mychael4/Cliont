import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;

public class Version {
    public static JPanel create() throws IOException, FontFormatException {

        Color darkGrayBackground = new Color(43, 43, 43);

        InputStream fontStream = Content.class.getResourceAsStream("/Minecraft.ttf");
        Font mcFont = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(24f);

        JPanel version = new JPanel(new CardLayout());
        version.setBackground(darkGrayBackground);
        version.setLayout(new BoxLayout(version, BoxLayout.Y_AXIS));

        JButton version16 = new JButton("1.16");
        version16.setAlignmentX(Component.LEFT_ALIGNMENT);
        version16.setAlignmentY(Component.TOP_ALIGNMENT);
        version16.setFont(mcFont);
        version16.setFocusPainted(false);
        version16.setBorderPainted(false);
        version16.setBackground(Color.black);
        version16.setMargin(new Insets(70, 20, 10, 20));

        JButton version17 = new JButton("1.17");
        version17.setAlignmentX(Component.LEFT_ALIGNMENT);
        version17.setAlignmentY(Component.TOP_ALIGNMENT);
        version17.setFont(mcFont);
        version17.setFocusPainted(false);
        version17.setBorderPainted(false);
        version17.setBackground(Color.black);
        version17.setMargin(new Insets(70, 20, 10, 20));



        version.add(version16);
        version.add(Box.createVerticalStrut(10));
        version.add(version17);

        return version;
    }
}
