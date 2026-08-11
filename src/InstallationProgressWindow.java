import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

public class InstallationProgressWindow extends JWindow {
    private float progress = 0f;
    private String profileName;
    private static final Color BG_COLOR = new Color(43, 43, 43);
    private static final Color BORDER_COLOR = new Color(150, 150, 150);
    private static final Color CLIONT_VIOLET = Main.cliont;
    private static final int POPUP_WIDTH = 300;
    private static final int POPUP_HEIGHT = 100;
    private static final int BORDER_RADIUS = 15;
    private static final int BORDER_WIDTH = 2;
    private static final int CLOSE_BUTTON_SIZE = 20;
    private Rectangle closeButtonBounds;

    public InstallationProgressWindow(String profileName) {
        this.profileName = profileName;
        
        setSize(POPUP_WIDTH, POPUP_HEIGHT);
        
        // Fenster transparent machen
        setBackground(new Color(0, 0, 0, 0));
        getContentPane().setBackground(new Color(0, 0, 0, 0));
        
        // Positioniere unten rechts in der Ecke
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        DisplayMode dm = gd.getDisplayMode();
        int screenWidth = dm.getWidth();
        int screenHeight = dm.getHeight();
        
        setLocation(screenWidth - POPUP_WIDTH - 20, screenHeight - POPUP_HEIGHT - 60);
        
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
                g2.drawRoundRect(BORDER_WIDTH/2, BORDER_WIDTH/2, width - BORDER_WIDTH, height - BORDER_WIDTH, BORDER_RADIUS, BORDER_RADIUS);
                
                // Zeichne Profil-Namen oben links
                g2.setColor(BORDER_COLOR);
                g2.setFont(new Font("Arial", Font.BOLD, 24));
                g2.drawString(profileName, padding + 5, padding + 20);
                
                // Zeichne Close-Button (X) oben rechts
                int closeX = width - padding - CLOSE_BUTTON_SIZE;
                int closeY = padding;
                closeButtonBounds = new Rectangle(closeX, closeY, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE);
                
                g2.setColor(BORDER_COLOR);
                g2.setStroke(new BasicStroke(2));
                int offset = 5;
                g2.drawLine(closeX + offset, closeY + offset, closeX + CLOSE_BUTTON_SIZE - offset, closeY + CLOSE_BUTTON_SIZE - offset);
                g2.drawLine(closeX + CLOSE_BUTTON_SIZE - offset, closeY + offset, closeX + offset, closeY + CLOSE_BUTTON_SIZE - offset);
                
                // Zeichne Progress Bar unten
                int barY = height - padding - 15;
                int barHeight = 8;
                int barWidth = width - 2 * padding - 4;
                
                // Hintergrund der Progress Bar
                g2.setColor(new Color(80, 80, 80));
                g2.fillRoundRect(padding + 2, barY, barWidth, barHeight, 4, 4);
                
                // Fortschritts-Balken (Violet)
                int progressWidth = (int) (barWidth * progress);
                g2.setColor(CLIONT_VIOLET);
                g2.fillRoundRect(padding + 2, barY, progressWidth, barHeight, 4, 4);
                
                g2.dispose();
            }
        };
        
        contentPanel.setBackground(BG_COLOR);
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
        
        // Fenster transparent und alwaysOnTop
        setAlwaysOnTop(true);
        setOpacity(1.0f);
    }
    
    public void setProgress(float progress) {
        this.progress = Math.max(0, Math.min(1, progress));
        repaint();
    }
    
    public void complete() {
        setProgress(1.0f);
        Timer timer = new Timer(500, e -> dispose());
        timer.setRepeats(false);
        timer.start();
    }
    
    public void showWindow() {
        SwingUtilities.invokeLater(() -> setVisible(true));
    }
}


