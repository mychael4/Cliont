import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;

public class Mods {

    static JPanel modsPanel;
    static CardLayout modsLayout;

    public static JPanel create() throws IOException, FontFormatException {

        modsLayout = new CardLayout();
        modsPanel = new JPanel(modsLayout);

        // 👉 CurseForge direkt als "browser" hinzufügen
        JPanel browser = CurseForgeBrowser.create();

        modsPanel.add(browser, "browser");
        modsLayout.show(modsPanel, "browser");

        return modsPanel; // 🔥 DAS ist entscheidend!
    }
}
