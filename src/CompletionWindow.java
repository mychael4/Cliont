import javax.swing.*;
import java.awt.*;

public class CompletionWindow extends JWindow {
    private static final Color BG_COLOR = new Color(43, 43, 43);
    private static final Color BORDER_COLOR = new Color(150, 150, 150);
    private static final Color CLIONT_VIOLET = Main.cliont;
    private static final int POPUP_WIDTH = 300;
    private static final int POPUP_HEIGHT = 60;
    private static final int BORDER_RADIUS = 15;
    private static final int BORDER_WIDTH = 2;
    private final String profileName;

    public CompletionWindow(String profileName) {
        this.profileName = profileName;

        setSize(POPUP_WIDTH, POPUP_HEIGHT);

        // Fenster transparent machen
        setBackground(new Color(0, 0, 0, 0));
        getContentPane().setBackground(new Color(0, 0, 0, 0));

        // Positioniere oben rechts in der Ecke
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        DisplayMode dm = gd.getDisplayMode();
        int screenWidth = dm.getWidth();

        setLocation(screenWidth - POPUP_WIDTH - 20, 20);

        JPanel contentPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int width = getWidth();
                int height = getHeight();
                int padding = 10;

                // Zeichne Hintergrund mit abgerundeten Ecken
                g2.setColor(BG_COLOR);
                g2.fillRoundRect(0, 0, width, height, BORDER_RADIUS, BORDER_RADIUS);

                // Zeichne Rand
                g2.setColor(BORDER_COLOR);
                g2.setStroke(new BasicStroke(BORDER_WIDTH));
                g2.drawRoundRect(BORDER_WIDTH / 2, BORDER_WIDTH / 2, width - BORDER_WIDTH, height - BORDER_WIDTH, BORDER_RADIUS, BORDER_RADIUS);

                // Zeichne Profil-Namen links
                g2.setColor(BORDER_COLOR);
                g2.setFont(new Font("Arial", Font.BOLD, 16));
                g2.drawString(profileName, padding + 5, padding + 18);

                // Zeichne "Done" Text rechts
                g2.setColor(CLIONT_VIOLET);
                g2.setFont(new Font("Arial", Font.BOLD, 16));
                FontMetrics fm = g2.getFontMetrics();
                int doneWidth = fm.stringWidth("Done");
                g2.drawString("Done", width - padding - 15 - doneWidth, padding + 18);

                g2.dispose();
            }
        };

        contentPanel.setBackground(BG_COLOR);
        contentPanel.setOpaque(false);

        add(contentPanel);

        // Fenster transparent und alwaysOnTop
        setAlwaysOnTop(true);
        setOpacity(1.0f);
    }

    public void showWindow() {
        SwingUtilities.invokeLater(() -> setVisible(true));

        // Fenster nach 6 Sekunden automatisch schließen
        Timer timer = new Timer(6000, e -> dispose());
        timer.setRepeats(false);
        timer.start();
    }
}

