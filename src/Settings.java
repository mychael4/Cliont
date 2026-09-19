import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

public class Settings {

    static JPanel settingsPanel;

    private static final int SWATCH_SIZE = 56;

    // Kept so a swatch can un-highlight its siblings when clicked.
    private static final Map<UIUtils.Theme, UIUtils.RoundedPanel> swatches = new EnumMap<>(UIUtils.Theme.class);

    public static JPanel create() throws IOException, FontFormatException {

        InputStream fontStream = Content.class.getResourceAsStream("/Minecraft.ttf");
        Font mcFont = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(24f);

        settingsPanel = new JPanel();
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        settingsPanel.setBackground(UIUtils.BG);
        settingsPanel.setBorder(new EmptyBorder(20, 30, 20, 30));

        JLabel titleLabel = new JLabel("Settings");
        titleLabel.setFont(mcFont.deriveFont(Font.BOLD, 24f));
        titleLabel.setForeground(UIUtils.TEXT);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));

        settingsPanel.add(titleLabel);

        settingsPanel.add(buildDesignSection(mcFont));

        // Add more settings sections below this line.

        return settingsPanel;
    }

    private static JPanel buildDesignSection(Font mcFont) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(UIUtils.BG);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        JLabel designLabel = new JLabel("Design");
        designLabel.setFont(mcFont.deriveFont(Font.BOLD, 18f));
        designLabel.setForeground(UIUtils.TEXT);
        designLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        designLabel.setBorder(new EmptyBorder(0, 0, 12, 0));
        section.add(designLabel);

        JPanel swatchRow = new JPanel();
        swatchRow.setLayout(new BoxLayout(swatchRow, BoxLayout.X_AXIS));
        swatchRow.setBackground(UIUtils.BG);
        swatchRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        swatches.clear();
        for (UIUtils.Theme theme : UIUtils.Theme.values()) {
            swatchRow.add(buildSwatch(theme, mcFont));
            swatchRow.add(Box.createHorizontalStrut(20));
        }

        section.add(swatchRow);
        return section;
    }

    private static JPanel buildSwatch(UIUtils.Theme theme, Font mcFont) {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(UIUtils.BG);

        UIUtils.RoundedPanel cube = new UIUtils.RoundedPanel(14, borderFor(theme));
        cube.setBackground(theme.swatchColor());
        cube.setPreferredSize(new Dimension(SWATCH_SIZE, SWATCH_SIZE));
        cube.setMaximumSize(new Dimension(SWATCH_SIZE, SWATCH_SIZE));
        cube.setAlignmentX(Component.CENTER_ALIGNMENT);
        cube.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cube.putClientProperty("cliont.themeExempt", true);

        swatches.put(theme, cube);

        cube.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                selectTheme(theme);
            }
        });

        JLabel caption = new JLabel(theme.label());
        caption.setFont(mcFont.deriveFont(13f));
        caption.setForeground(UIUtils.TEXT_MUTED);
        caption.setAlignmentX(Component.CENTER_ALIGNMENT);
        caption.setBorder(new EmptyBorder(8, 0, 0, 0));

        wrapper.add(cube);
        wrapper.add(caption);
        return wrapper;
    }

    /** Selected swatch gets the purple accent ring; the rest get a plain border. */
    private static Color borderFor(UIUtils.Theme theme) {
        return theme == UIUtils.getCurrentTheme() ? Main.cliont : UIUtils.LIGHT_BORDER;
    }

    private static void selectTheme(UIUtils.Theme newTheme) {
        UIUtils.Theme oldTheme = UIUtils.getCurrentTheme();
        if (newTheme == oldTheme) {
            return;
        }

        UIUtils.applyTheme(newTheme, true);

        Component root = Main.frame != null ? Main.frame.getContentPane() : settingsPanel;
        UIUtils.retheme(root, oldTheme);
        Main.refreshTitleBarTheme();
        for (Window w : Window.getWindows()) {
            w.repaint();
        }

        // Re-ring the swatches so the newly-picked one shows as active.
        for (Map.Entry<UIUtils.Theme, UIUtils.RoundedPanel> entry : swatches.entrySet()) {
            entry.getValue().setBorderColor(borderFor(entry.getKey()));
        }
    }
}