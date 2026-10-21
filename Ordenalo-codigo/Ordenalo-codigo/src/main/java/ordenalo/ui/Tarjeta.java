package ordenalo.ui;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/** Panel con fondo y esquinas redondeadas; se adapta solo al tema claro u oscuro. */
final class Tarjeta extends JPanel {

    Tarjeta(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int arco = UIScale.scale(16);
        g2.setColor(Estilo.fondoTarjeta());
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arco, arco);
        g2.setColor(Estilo.bordeTarjeta());
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arco, arco);
        g2.dispose();
        super.paintComponent(g);
    }
}
