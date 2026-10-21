package ordenalo.ui;

import com.formdev.flatlaf.util.UIScale;
import ordenalo.modelo.Elemento;

import javax.swing.Icon;
import javax.swing.UIManager;
import javax.swing.filechooser.FileSystemView;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Iconos dibujados por código (así no hacen falta imágenes) y los iconos de archivo del sistema. */
final class Iconos {

    private static final Map<String, Icon> CACHE = new HashMap<>();
    private static final Set<String> ICONO_PROPIO = Set.of("exe", "lnk", "url", "ico", "msi", "appx", "appimage");

    private Iconos() {
    }

    static void limpiarCache() {
        CACHE.clear();
    }

    // ─── Iconos de archivos ──────────────────────────────────────────────

    static Icon deElemento(Elemento e) {
        if (e.getRuta() == null) {
            return e.esCarpeta() ? carpeta() : archivo();
        }
        String clave;
        if (e.esCarpeta()) {
            clave = "carpeta";
        } else if (ICONO_PROPIO.contains(e.getExtension())) {
            clave = e.getRuta().toString();
        } else {
            clave = "ext:" + e.getExtension();
        }
        return CACHE.computeIfAbsent(clave, k -> delSistema(e.getRuta().toFile(), e.esCarpeta()));
    }

    private static Icon delSistema(File f, boolean esCarpeta) {
        try {
            Icon i = FileSystemView.getFileSystemView().getSystemIcon(f, UIScale.scale(16), UIScale.scale(16));
            if (i != null) {
                return i;
            }
        } catch (RuntimeException e) {
            // sin icono del sistema
        }
        return esCarpeta ? carpeta() : archivo();
    }

    static Icon carpeta() {
        Icon i = UIManager.getIcon("FileView.directoryIcon");
        return i != null ? i : UIManager.getIcon("Tree.closedIcon");
    }

    static Icon archivo() {
        Icon i = UIManager.getIcon("FileView.fileIcon");
        return i != null ? i : UIManager.getIcon("Tree.leafIcon");
    }

    // ─── Iconos dibujados ────────────────────────────────────────────────

    /** Un punto de color (el color de cada categoría). */
    static Icon punto(Color color) {
        return punto(color, 10);
    }

    static Icon punto(Color color, int tamano) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = suave(g);
                int s = UIScale.scale(tamano);
                g2.setColor(color);
                g2.fill(new Ellipse2D.Float(x, y, s, s));
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return UIScale.scale(tamano);
            }

            @Override
            public int getIconHeight() {
                return UIScale.scale(tamano);
            }
        };
    }

    /** Círculo con un número, para los pasos 1 y 2. */
    static Icon numero(int n) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = suave(g);
                int s = getIconWidth();
                g2.setColor(Estilo.acento());
                g2.fill(new Ellipse2D.Float(x, y, s, s));
                g2.setColor(Color.WHITE);
                g2.setFont(c.getFont().deriveFont(Font.BOLD, UIScale.scale(12f)));
                FontMetrics fm = g2.getFontMetrics();
                String t = Integer.toString(n);
                g2.drawString(t, x + (s - fm.stringWidth(t)) / 2f, y + (s - fm.getHeight()) / 2f + fm.getAscent());
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return UIScale.scale(22);
            }

            @Override
            public int getIconHeight() {
                return UIScale.scale(22);
            }
        };
    }

    /** Media luna para el botón de modo oscuro. */
    static Icon luna() {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = suave(g);
                float s = getIconWidth();
                Area luna = new Area(new Ellipse2D.Float(x + s * 0.1f, y + s * 0.1f, s * 0.8f, s * 0.8f));
                luna.subtract(new Area(new Ellipse2D.Float(x + s * 0.38f, y - s * 0.05f, s * 0.72f, s * 0.72f)));
                g2.setColor(c.getForeground());
                g2.fill(luna);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return UIScale.scale(18);
            }

            @Override
            public int getIconHeight() {
                return UIScale.scale(18);
            }
        };
    }

    /** El icono de la aplicación: una carpeta con una marca de «hecho». */
    static BufferedImage dibujarApp(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        float r = s * 0.24f;
        g.setPaint(new GradientPaint(0, 0, new Color(0x6E7BF2), s, s, new Color(0x3B44B8)));
        g.fill(new RoundRectangle2D.Float(0, 0, s, s, r * 2, r * 2));

        float m = s * 0.18f;
        float w = s - 2 * m;
        float h = w * 0.70f;
        float arriba = (s - h) / 2f + s * 0.05f;
        g.setColor(new Color(255, 255, 255, 120));
        g.fill(new RoundRectangle2D.Float(m, arriba - h * 0.16f, w * 0.46f, h * 0.36f, s * 0.09f, s * 0.09f));
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Float(m, arriba, w, h, s * 0.11f, s * 0.11f));

        g.setColor(new Color(0x4A55D4));
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.08f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D marca = new Path2D.Float();
        marca.moveTo(m + w * 0.30f, arriba + h * 0.52f);
        marca.lineTo(m + w * 0.45f, arriba + h * 0.70f);
        marca.lineTo(m + w * 0.72f, arriba + h * 0.32f);
        g.draw(marca);
        g.dispose();
        return img;
    }

    static List<Image> iconosVentana() {
        List<Image> lista = new ArrayList<>();
        for (int s : new int[]{16, 20, 24, 32, 40, 48, 64, 128, 256}) {
            lista.add(dibujarApp(s));
        }
        return lista;
    }

    private static Graphics2D suave(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }
}
