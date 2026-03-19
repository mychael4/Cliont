import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;

public class Mods {
    public static JPanel create() throws IOException, FontFormatException {

        Color darkGrayBackground = new Color(43, 43, 43);
        Color Cliont = new Color(188, 19, 254);

        InputStream fontStream = Content.class.getResourceAsStream("/Minecraft.ttf");
        Font mcFont = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(24f);

        JPanel mods = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        mods.setBackground(darkGrayBackground);
        mods.setLayout(new BoxLayout(mods, BoxLayout.Y_AXIS));

        JButton SearchMods = new JButton("Discover Mods");
        SearchMods.setAlignmentX(Component.CENTER_ALIGNMENT);
        SearchMods.setFont(mcFont);
        SearchMods.setBackground(Cliont);

        SearchMods.addActionListener(e -> {
            CurseForgeBrowser.create();
        });


        //mods.add(SearchMods);

        return mods;
    }
}
