import javax.swing.*;
import java.awt.*;

public class UIUtils {

    public static final Color BG = new Color(43, 43, 43);
    public static final Color CARD = new Color(60, 60, 60);
    public static final Color CLIONT = new Color(188, 19, 254);
    public static final Color LIGHT_BORDER = new Color(100, 100, 100);

    public static class RoundedPanel extends JPanel {
        private final int radius;
        private final Color borderColor;

        public RoundedPanel(int radius, Color borderColor) {
            this.radius = radius;
            this.borderColor = borderColor;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);

            if (borderColor != null) {
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, radius, radius);
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

}

