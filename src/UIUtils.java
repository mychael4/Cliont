import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class UIUtils {

    // --- Embedded Theme Enum ---
    public enum Theme {
        GRAY(
                "Gray Theme",        // label
                new Color(0x242424), // swatchColor (and bg)
                new Color(0x242424), // bg
                new Color(0x1E1E1E), // sidebar
                new Color(0x2D2D2D), // card
                new Color(0x333333), // input
                new Color(0x3F3F3F), // border
                new Color(0x4A4A4A), // button
                new Color(0x555555), // hover
                new Color(0xE0E0E0), // text
                new Color(0xAAAAAA)  // textMuted
        ),
        LIGHT(
                "Light Theme",       // label
                new Color(0xF5F5F5), // swatchColor (and bg)
                new Color(0xF5F5F5), // bg
                new Color(0xEAEAEA), // sidebar
                new Color(0xFFFFFF), // card
                new Color(0xF0F0F0), // input
                new Color(0xDCDCDC), // border
                new Color(0xE1E1E1), // button
                new Color(0xD5D5D5), // hover
                new Color(0x222222), // text
                new Color(0x777777)  // textMuted
        ),
        DARK(
                "Dark Theme",        // label
                new Color(0x121212), // swatchColor (and bg)
                new Color(0x121212), // bg
                new Color(0x1C1C1C), // sidebar
                new Color(0x1E1E1E), // card
                new Color(0x2A2A2A), // input
                new Color(0x333333), // border
                new Color(0x3A3A3A), // button
                new Color(0x444444), // hover
                new Color(0xFFFFFF), // text
                new Color(0x888888)  // textMuted
        );

        private final String label;
        private final Color swatchColor;
        public final Color bg;
        public final Color sidebar;
        public final Color card;
        public final Color input;
        public final Color border;
        public final Color button;
        public final Color hover;
        public final Color text;
        public final Color textMuted;

        Theme(String label, Color swatchColor, Color bg, Color sidebar, Color card, Color input,
              Color border, Color button, Color hover, Color text, Color textMuted) {
            this.label = label;
            this.swatchColor = swatchColor;
            this.bg = bg;
            this.sidebar = sidebar;
            this.card = card;
            this.input = input;
            this.border = border;
            this.button = button;
            this.hover = hover;
            this.text = text;
            this.textMuted = textMuted;
        }

        public String label() {
            return label;
        }

        public Color swatchColor() {
            return swatchColor;
        }
    }
    // ---------------------------

    // Shared, theme-driven colors. Not `final` anymore: Settings > Design
    // swaps these live via applyTheme() + retheme(). Everything that used to
    // read UIUtils.BG / .CARD / .LIGHT_BORDER keeps working the same way -
    // the values underneath now just depend on the chosen theme.
    public static Color BG;
    public static Color SIDEBAR;
    public static Color CARD;
    public static Color INPUT;
    public static Color LIGHT_BORDER;
    public static Color BUTTON;
    public static Color HOVER;
    public static Color TEXT;
    public static Color TEXT_MUTED;

    /** Brand purple. Same in every theme - it never changes. */
    public static final Color CLIONT = Main.cliont;

    private static Theme currentTheme;

    private static final Path SETTINGS_FILE =
            Paths.get(System.getProperty("user.home"), ".cliont", "settings.json");

    static {
        applyTheme(loadSavedTheme(), false);
    }

    public static Theme getCurrentTheme() {
        return currentTheme;
    }

    /** Reads the last-saved Design choice from disk, defaulting to GRAY (today's look). */
    public static Theme loadSavedTheme() {
        try {
            if (Files.exists(SETTINGS_FILE)) {
                String json = new String(Files.readAllBytes(SETTINGS_FILE));
                SettingsData data = new Gson().fromJson(json, SettingsData.class);
                if (data != null && data.theme != null) {
                    return Theme.valueOf(data.theme);
                }
            }
        } catch (Exception ignored) {
            // Corrupt/missing file - fall back to default below.
        }
        return Theme.GRAY;
    }

    private static void saveTheme(Theme theme) {
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            SettingsData data = new SettingsData();
            data.theme = theme.name();
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(data);
            Files.write(SETTINGS_FILE, json.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Points BG/CARD/etc. at the given theme's colors.
     * Pass persist=true to also write the choice to disk (used when the
     * user actually clicks a swatch; startup loading uses persist=false).
     */
    public static void applyTheme(Theme theme, boolean persist) {
        currentTheme = theme;
        BG = theme.bg;
        SIDEBAR = theme.sidebar;
        CARD = theme.card;
        INPUT = theme.input;
        LIGHT_BORDER = theme.border;
        BUTTON = theme.button;
        HOVER = theme.hover;
        TEXT = theme.text;
        TEXT_MUTED = theme.textMuted;
        if (persist) {
            saveTheme(theme);
        }
    }

    public static Color alpha(Color color, int alpha) {
        if (color == null) {
            return new Color(0, 0, 0, 0);
        }
        int a = Math.max(0, Math.min(255, alpha));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

    public static void styleScrollPane(JScrollPane scrollPane) {
        if (scrollPane == null) return;
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

        JScrollBar verticalBar = scrollPane.getVerticalScrollBar();
        verticalBar.setUI(new ModernScrollBarUI());
        verticalBar.setOpaque(false);
        verticalBar.setUnitIncrement(16);

        JScrollBar horizontalBar = scrollPane.getHorizontalScrollBar();
        horizontalBar.setUI(new ModernScrollBarUI());
        horizontalBar.setOpaque(false);
        horizontalBar.setUnitIncrement(16);
    }

    public static final class ModernScrollBarUI extends BasicScrollBarUI {
        private static final int THUMB_SIZE = 10;

        @Override
        protected void configureScrollBarColors() {
            thumbColor = alpha(CLIONT, 180);
            thumbDarkShadowColor = alpha(CLIONT.darker(), 180);
            thumbLightShadowColor = alpha(CLIONT.brighter(), 180);
            trackColor = new Color(0, 0, 0, 0);
            trackHighlightColor = new Color(0, 0, 0, 0);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createZeroButton();
        }

        private JButton createZeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0, 0, 0, 0));
            g2.fillRoundRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height, 12, 12);
            g2.dispose();
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !(c instanceof JScrollBar) || !c.isEnabled()) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(alpha(CLIONT, 170));

            int x = thumbBounds.x + 2;
            int y = thumbBounds.y + 2;
            int width = thumbBounds.width - 4;
            int height = thumbBounds.height - 4;
            if (width <= 0 || height <= 0) return;

            g2.fillRoundRect(x, y, width, height, 10, 10);
            g2.dispose();
        }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            return new Dimension(THUMB_SIZE, THUMB_SIZE);
        }
    }

    /**
     * Walks the component tree from `root` and re-colors any component whose
     * background/foreground/border still matches one of oldTheme's colors,
     * swapping it for the same role in the now-current theme. Call this right
     * after applyTheme(newTheme, true) so already-built panels (sidebar, Home,
     * Settings) update immediately, without restarting the app.
     */
    public static void retheme(Component root, Theme oldTheme) {
        remap(root, oldTheme);
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                retheme(child, oldTheme);
            }
        }
        root.revalidate();
        root.repaint();
    }

    private static void remap(Component c, Theme old) {
        if (c instanceof JComponent) {
            Object exempt = ((JComponent) c).getClientProperty("cliont.themeExempt");
            if (Boolean.TRUE.equals(exempt)) {
                return;
            }
        }

        Color bg = c.getBackground();
        if (bg != null) {
            if (sameColor(bg, old.bg)) c.setBackground(BG);
            else if (sameColor(bg, old.sidebar)) c.setBackground(SIDEBAR);
            else if (sameColor(bg, old.card)) c.setBackground(CARD);
            else if (sameColor(bg, old.input)) c.setBackground(INPUT);
            else if (sameColor(bg, old.button)) c.setBackground(BUTTON);
            else if (sameColor(bg, old.hover)) c.setBackground(HOVER);
        }

        Color fg = c.getForeground();
        if (fg != null) {
            if (sameColor(fg, old.text)) c.setForeground(TEXT);
            else if (sameColor(fg, old.textMuted)) c.setForeground(TEXT_MUTED);
        }

        if (c instanceof RoundedPanel) {
            RoundedPanel rp = (RoundedPanel) c;
            if (sameColor(rp.getBorderColor(), old.border)) {
                rp.setBorderColor(LIGHT_BORDER);
            }
        }
        if (c instanceof Content.RoundedPanel) {
            Content.RoundedPanel rp = (Content.RoundedPanel) c;
            if (sameColor(rp.getBorderColor(), old.border)) {
                rp.setBorderColor(LIGHT_BORDER);
            }
        }
    }

    private static boolean sameColor(Color a, Color b) {
        return a != null && b != null && a.getRGB() == b.getRGB();
    }

    private static class SettingsData {
        String theme;
    }

    public static class RoundedPanel extends JPanel {
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
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = getBackground() == null ? new Color(0, 0, 0, 0) : getBackground();
            int alpha = base.getAlpha() == 255 ? 150 : Math.max(40, base.getAlpha());
            g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);

            if (borderColor != null) {
                g2.setColor(alpha(borderColor, 80));
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, radius, radius);
            }

            g2.dispose();
        }
    }
}
