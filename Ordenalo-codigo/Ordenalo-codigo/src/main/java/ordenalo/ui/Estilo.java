package ordenalo.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.ColorFunctions;
import ordenalo.modelo.Categoria;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.Map;

/** Colores, tipografía y aspecto general (tema claro y oscuro con FlatLaf). */
final class Estilo {

    static final String ACENTO = "#4f5bd5";

    private Estilo() {
    }

    static void aplicarTema(boolean oscuro) {
        FlatLaf.setGlobalExtraDefaults(Map.of("@accentColor", ACENTO));
        if (oscuro) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("CheckBox.arc", 6);
        UIManager.put("ProgressBar.arc", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("Table.rowHeight", 30);
        UIManager.put("TableHeader.height", 30);
        UIManager.put("Tree.rowHeight", 26);
        UIManager.put("TabbedPane.tabHeight", 44);
        UIManager.put("TabbedPane.showTabSeparators", false);
        UIManager.put("TitlePane.unifiedBackground", true);
        UIManager.put("SplitPaneDivider.style", "plain");
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
        UIManager.put("Table.cellFocusColor", new Color(0, 0, 0, 0));
    }

    static boolean oscuro() {
        return FlatLaf.isLafDark();
    }

    static Color acento() {
        Color c = UIManager.getColor("Component.accentColor");
        return c != null ? c : Color.decode(ACENTO);
    }

    static Color gris() {
        Color c = UIManager.getColor("Label.disabledForeground");
        return c != null ? c : Color.GRAY;
    }

    static Color fondoTarjeta() {
        Color c = UIManager.getColor("Table.background");
        return c != null ? c : Color.WHITE;
    }

    static Color bordeTarjeta() {
        Color panel = UIManager.getColor("Panel.background");
        if (panel == null) {
            return new Color(0xE3E5EA);
        }
        return oscuro() ? ColorFunctions.lighten(panel, 0.08f) : new Color(0xE1E4EA);
    }

    static Color colorDe(Categoria c) {
        try {
            return Color.decode(c.getColor());
        } catch (RuntimeException e) {
            return gris();
        }
    }

    static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    static String html(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static JLabel titulo(String texto) {
        JLabel l = new JLabel(texto);
        l.putClientProperty(FlatClientProperties.STYLE_CLASS, "h3");
        return l;
    }

    static JLabel subtitulo(String texto) {
        JLabel l = new JLabel(texto);
        l.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        return l;
    }

    static void claseEstilo(JComponent c, String clase) {
        c.putClientProperty(FlatClientProperties.STYLE_CLASS, clase);
    }

    /** Botón destacado, relleno con el color de acento. */
    static JButton principal(String texto) {
        JButton b = new JButton(texto);
        b.putClientProperty(FlatClientProperties.STYLE,
                "background: " + ACENTO + ";"
                        + "foreground: #ffffff;"
                        + "hoverBackground: darken(" + ACENTO + ",6%);"
                        + "pressedBackground: darken(" + ACENTO + ",12%);"
                        + "borderColor: " + ACENTO + ";"
                        + "hoverBorderColor: darken(" + ACENTO + ",6%);"
                        + "focusedBorderColor: lighten(" + ACENTO + ",18%);"
                        + "font: bold");
        return b;
    }

    /** Texto de ayuda pequeño y gris que no obliga a ensanchar el formulario. */
    static JLabel ayuda(String texto) {
        JLabel l = new JLabel("<html>" + html(texto) + "</html>") {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(Math.min(d.width, com.formdev.flatlaf.util.UIScale.scale(360)), d.height);
            }
        };
        l.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        l.putClientProperty(FlatClientProperties.STYLE_CLASS, "small");
        return l;
    }
}
