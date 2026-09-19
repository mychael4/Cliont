import javax.swing.*;
import java.awt.*;

public class CompletionWindow extends JDialog {
    private static final Color CLIONT_VIOLET = Main.cliont;
    private static final int POPUP_WIDTH = 300;
    private static final int POPUP_HEIGHT = 60;
    private static final int BORDER_RADIUS = 15;
    private static final int BORDER_WIDTH = 2;
    private final String profileName;

    public CompletionWindow(String profileName) {
        super(Main.frame != null ? Main.frame : null, "", ModalityType.MODELESS);
        this.profileName = profileName;

        setUndecorated(true);
        setResizable(false);
        setFocusableWindowState(false);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(POPUP_WIDTH, POPUP_HEIGHT);
        setAlwaysOnTop(true);
        setBackground(new Color(0, 0, 0, 0));
        getContentPane().setBackground(new Color(0, 0, 0, 0));

        if (Main.frame != null) {
            setLocationRelativeTo(Main.frame);
            setLocation(Main.frame.getX() + Main.frame.getWidth() - POPUP_WIDTH - 24,
                    Main.frame.getY() + 24);
        } else {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = ge.getDefaultScreenDevice();
            DisplayMode dm = gd.getDisplayMode();
            setLocation(dm.getWidth() - POPUP_WIDTH - 20, 20);
        }

        JPanel contentPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int width = getWidth();
                int height = getHeight();
                int padding = 10;

                Color bg = UIUtils.BG;
                Color border = UIUtils.LIGHT_BORDER;
                Color text = UIUtils.TEXT;

                g2.setColor(bg);
                g2.fillRoundRect(0, 0, width, height, BORDER_RADIUS, BORDER_RADIUS);

                g2.setColor(border);
                g2.setStroke(new BasicStroke(BORDER_WIDTH));
                g2.drawRoundRect(BORDER_WIDTH / 2, BORDER_WIDTH / 2, width - BORDER_WIDTH, height - BORDER_WIDTH, BORDER_RADIUS, BORDER_RADIUS);

                g2.setColor(text);
                g2.setFont(new Font("Arial", Font.BOLD, 16));
                g2.drawString(profileName, padding + 5, padding + 18);

                g2.setColor(CLIONT_VIOLET);
                g2.setFont(new Font("Arial", Font.BOLD, 16));
                FontMetrics fm = g2.getFontMetrics();
                int doneWidth = fm.stringWidth("Done");
                g2.drawString("Done", width - padding - 15 - doneWidth, padding + 18);

                g2.dispose();
            }
        };

        contentPanel.setBackground(UIUtils.BG);
        contentPanel.setOpaque(false);

        add(contentPanel);
        setOpacity(1.0f);
    }

    public void showWindow() {
        Runnable r = () -> {
            setVisible(true);
            toFront();
            requestFocus();
        };

        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }

        // Fenster nach 6 Sekunden automatisch schließen
        Timer timer = new Timer(6000, e -> dispose());
        timer.setRepeats(false);
        timer.start();
    }
}

