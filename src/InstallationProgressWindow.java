import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

public class InstallationProgressWindow extends JDialog {
    private float progress = 0f;
    private String profileName;
    private static final Color CLIONT_VIOLET = Main.cliont;
    private static final int POPUP_WIDTH = 300;
    private static final int POPUP_HEIGHT = 100;
    private static final int BORDER_RADIUS = 15;
    private static final int BORDER_WIDTH = 2;
    private static final int CLOSE_BUTTON_SIZE = 20;
    private Rectangle closeButtonBounds;

    public InstallationProgressWindow(String profileName) {
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
                    Main.frame.getY() + Main.frame.getHeight() - POPUP_HEIGHT - 28);
        } else {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = ge.getDefaultScreenDevice();
            DisplayMode dm = gd.getDisplayMode();
            setLocation(dm.getWidth() - POPUP_WIDTH - 20, dm.getHeight() - POPUP_HEIGHT - 60);
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
                Color mutedBar = UIUtils.BUTTON;

                g2.setColor(bg);
                g2.fillRoundRect(0, 0, width, height, BORDER_RADIUS, BORDER_RADIUS);

                g2.setColor(border);
                g2.setStroke(new BasicStroke(BORDER_WIDTH));
                g2.drawRoundRect(BORDER_WIDTH/2, BORDER_WIDTH/2, width - BORDER_WIDTH, height - BORDER_WIDTH, BORDER_RADIUS, BORDER_RADIUS);

                g2.setColor(text);
                g2.setFont(new Font("Arial", Font.BOLD, 24));
                g2.drawString(profileName, padding + 5, padding + 20);

                int closeX = width - padding - CLOSE_BUTTON_SIZE;
                int closeY = padding;
                closeButtonBounds = new Rectangle(closeX, closeY, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE);

                g2.setColor(border);
                g2.setStroke(new BasicStroke(2));
                int offset = 5;
                g2.drawLine(closeX + offset, closeY + offset, closeX + CLOSE_BUTTON_SIZE - offset, closeY + CLOSE_BUTTON_SIZE - offset);
                g2.drawLine(closeX + CLOSE_BUTTON_SIZE - offset, closeY + offset, closeX + offset, closeY + CLOSE_BUTTON_SIZE - offset);

                int barY = height - padding - 15;
                int barHeight = 8;
                int barWidth = width - 2 * padding - 4;

                g2.setColor(mutedBar);
                g2.fillRoundRect(padding + 2, barY, barWidth, barHeight, 4, 4);

                int progressWidth = (int) (barWidth * progress);
                g2.setColor(CLIONT_VIOLET);
                g2.fillRoundRect(padding + 2, barY, progressWidth, barHeight, 4, 4);
                
                g2.dispose();
            }
        };
        
        contentPanel.setBackground(UIUtils.BG);
        contentPanel.setOpaque(false);
        
        // Mouse Listener für Close Button
        contentPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (closeButtonBounds != null && closeButtonBounds.contains(e.getPoint())) {
                    dispose();
                }
            }
        });
        
        add(contentPanel);
        setOpacity(1.0f);
    }
    
    public void setProgress(float progress) {
        this.progress = Math.max(0, Math.min(1, progress));
        Runnable r = this::repaint;
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }
    
    public void complete() {
        setProgress(1.0f);
        Timer timer = new Timer(500, e -> dispose());
        timer.setRepeats(false);
        timer.start();
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
    }
}


