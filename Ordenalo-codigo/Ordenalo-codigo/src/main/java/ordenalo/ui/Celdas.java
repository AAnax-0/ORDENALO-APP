package ordenalo.ui;

import com.formdev.flatlaf.util.UIScale;
import ordenalo.modelo.Accion;
import ordenalo.modelo.Categoria;
import ordenalo.modelo.Confianza;
import ordenalo.modelo.Elemento;
import ordenalo.modelo.Propuesta;
import ordenalo.motor.Texto;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Cómo se pinta cada columna de la tabla de propuestas. */
final class Celdas {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private Celdas() {
    }

    private static Propuesta propuesta(Object valor) {
        return (Propuesta) valor;
    }

    /** Casilla para marcar; no aparece en lo que se queda donde está. */
    static final class Marca implements TableCellRenderer {
        private final JCheckBox casilla = new JCheckBox();
        private final JLabel vacio = new JLabel();

        Marca() {
            casilla.setHorizontalAlignment(SwingConstants.CENTER);
            casilla.setOpaque(true);
            vacio.setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionada,
                                                       boolean foco, int fila, int columna) {
            Propuesta p = (Propuesta) tabla.getModel().getValueAt(tabla.convertRowIndexToModel(fila), ModeloTabla.ELEMENTO);
            JComponent c = p.getAccion() == Accion.MOVER ? casilla : vacio;
            casilla.setSelected(Boolean.TRUE.equals(valor));
            c.setBackground(seleccionada ? tabla.getSelectionBackground() : tabla.getBackground());
            casilla.setToolTipText(p.dudosa() ? "Dudoso: márcalo si quieres moverlo" : null);
            return c;
        }
    }

    /** Icono + nombre del archivo o carpeta. */
    static final class NombreElemento extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionada,
                                                       boolean foco, int fila, int columna) {
            super.getTableCellRendererComponent(tabla, "", seleccionada, false, fila, columna);
            Elemento e = propuesta(valor).getElemento();
            setIcon(Iconos.deElemento(e));
            setIconTextGap(UIScale.scale(8));
            setText(e.getNombre());
            setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            setToolTipText(descripcion(e));
            return this;
        }

        static String descripcion(Elemento e) {
            StringBuilder sb = new StringBuilder("<html><b>").append(Estilo.html(e.getNombre())).append("</b><br>");
            if (e.esCarpeta()) {
                sb.append("Carpeta con ").append(e.getArchivosDentro()).append(e.isContenidoIncompleto() ? "+" : "")
                        .append(e.getArchivosDentro() == 1 ? " archivo" : " archivos").append(" · ").append(Texto.tamano(e.getTamano()));
            } else {
                sb.append(Texto.tamano(e.getTamano()));
            }
            if (e.getModificado() > 0) {
                sb.append("<br>Modificado el ").append(FECHA.format(Instant.ofEpochMilli(e.getModificado())));
            }
            sb.append("<br><br>Doble clic para abrirlo y ver qué es</html>");
            return sb.toString();
        }
    }

    /** Punto de color + categoría, con una flechita que indica que se puede cambiar. */
    static final class Destino extends DefaultTableCellRenderer {
        private boolean flecha;

        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionada,
                                                       boolean foco, int fila, int columna) {
            super.getTableCellRendererComponent(tabla, "", seleccionada, false, fila, columna);
            Propuesta p = propuesta(valor);
            Categoria c = p.categoria();
            setIcon(Iconos.punto(Estilo.colorDe(c)));
            setIconTextGap(UIScale.scale(8));
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 22));
            Font base = tabla.getFont();
            setFont(p.isElegidaPorUsuario() ? base.deriveFont(Font.BOLD) : base);
            if (p.getAccion() == Accion.DEJAR) {
                setText(c.getTipo() == Categoria.Tipo.DEJAR ? "Se queda aquí" : c.getNombre() + " (se queda)");
                if (!seleccionada) {
                    setForeground(Estilo.gris());
                }
            } else {
                setText(c.getNombre());
            }
            setToolTipText("Haz clic para elegir a dónde va");
            flecha = true;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!flecha) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float s = UIScale.scale(4f);
            float x = getWidth() - UIScale.scale(12f);
            float y = getHeight() / 2f - s / 2f;
            Path2D v = new Path2D.Float();
            v.moveTo(x - s, y);
            v.lineTo(x, y + s);
            v.lineTo(x + s, y);
            g2.setColor(getForeground());
            g2.setStroke(new BasicStroke(UIScale.scale(1.4f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(v);
            g2.dispose();
        }
    }

    /** Subcarpeta dentro de la asignatura (Tarea 3, Ejercicios…). */
    static final class Subcarpeta extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionada,
                                                       boolean foco, int fila, int columna) {
            Propuesta p = (Propuesta) tabla.getModel().getValueAt(tabla.convertRowIndexToModel(fila), ModeloTabla.ELEMENTO);
            String texto = p.getAccion() == Accion.MOVER ? p.getSubcarpeta() : "";
            boolean editable = p.getAccion() == Accion.MOVER && p.categoria().getTipo() == Categoria.Tipo.ASIGNATURA;
            super.getTableCellRendererComponent(tabla, texto.isEmpty() && editable ? "—" : texto, seleccionada, false, fila, columna);
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            if (texto.isEmpty() && !seleccionada) {
                setForeground(Estilo.gris());
            }
            setToolTipText(editable ? "Doble clic para escribir otra subcarpeta (vacío = directamente en la asignatura)" : null);
            return this;
        }
    }

    /** Explicación en gris. */
    static final class Motivo extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionada,
                                                       boolean foco, int fila, int columna) {
            Propuesta p = propuesta(valor);
            String texto = p.motivo();
            super.getTableCellRendererComponent(tabla, texto, seleccionada, false, fila, columna);
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            if (!seleccionada) {
                setForeground(Estilo.gris());
            }
            String destino = p.getDestino() == null ? "Se queda donde está" : "Irá a: " + Estilo.html(p.getDestino().toString());
            setToolTipText("<html><div style='width:360px'>" + Estilo.html(texto) + "<br><br>" + destino + "</div></html>");
            return this;
        }
    }

    /** Píldora de color: segura, probable, dudosa… */
    static final class Pildora extends JComponent implements TableCellRenderer {
        private String texto;
        private Color fondo;
        private Color letra;
        private Color fondoFila;

        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionada,
                                                       boolean foco, int fila, int columna) {
            Propuesta p = propuesta(valor);
            fondoFila = seleccionada ? tabla.getSelectionBackground() : tabla.getBackground();
            setFont(tabla.getFont().deriveFont(Font.BOLD, tabla.getFont().getSize2D() - 1f));
            boolean oscuro = Estilo.oscuro();
            if (p.getAccion() == Accion.DEJAR) {
                texto = null;
                setToolTipText(null);
            } else if (p.isElegidaPorUsuario()) {
                poner("Elegido", oscuro ? 0x312e81 : 0xe0e7ff, oscuro ? 0xc7d2fe : 0x3730a3);
                setToolTipText("Lo has decidido tú");
            } else if (p.getConfianza() == Confianza.ALTA) {
                poner("Segura", oscuro ? 0x14532d : 0xdcfce7, oscuro ? 0xbbf7d0 : 0x166534);
                setToolTipText("Está clarísimo por el nombre o el tipo de archivo");
            } else if (p.getConfianza() == Confianza.MEDIA) {
                poner("Probable", oscuro ? 0x713f12 : 0xfef3c7, oscuro ? 0xfde68a : 0x92400e);
                setToolTipText("Seguramente acierta, pero échale un ojo");
            } else {
                poner("Dudosa", oscuro ? 0x7f1d1d : 0xfee2e2, oscuro ? 0xfecaca : 0x991b1b);
                setToolTipText("No está claro: no se moverá si no lo marcas tú");
            }
            return this;
        }

        private void poner(String t, int f, int l) {
            texto = t;
            fondo = new Color(f);
            letra = new Color(l);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(fondoFila);
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (texto != null) {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int pad = UIScale.scale(8);
                int w = fm.stringWidth(texto) + pad * 2;
                int h = fm.getHeight() + UIScale.scale(2);
                int x = UIScale.scale(8);
                int y = (getHeight() - h) / 2;
                g2.setColor(fondo);
                g2.fillRoundRect(x, y, w, h, h, h);
                g2.setColor(letra);
                g2.drawString(texto, x + pad, y + (h - fm.getHeight()) / 2 + fm.getAscent());
            }
            g2.dispose();
        }
    }

    /** Editor de «Va a»: un desplegable con todas las categorías. */
    static final class EditorDestino extends DefaultCellEditor {
        EditorDestino(List<Categoria> categorias) {
            super(crearCombo(categorias));
            setClickCountToStart(1);
        }

        private static JComboBox<Categoria> crearCombo(List<Categoria> categorias) {
            JComboBox<Categoria> combo = new JComboBox<>(categorias.toArray(new Categoria[0]));
            combo.setRenderer(new RenderCategoria());
            combo.setMaximumRowCount(18);
            return combo;
        }

        @Override
        public Component getTableCellEditorComponent(JTable tabla, Object valor, boolean seleccionada, int fila, int columna) {
            Object categoria = valor instanceof Propuesta p ? p.categoria() : valor;
            return super.getTableCellEditorComponent(tabla, categoria, seleccionada, fila, columna);
        }
    }

    /** Cómo se ve una categoría en las listas: punto de color, nombre y, en gris, de qué tipo es. */
    static final class RenderCategoria extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> lista, Object valor, int indice,
                                                      boolean seleccionada, boolean foco) {
            super.getListCellRendererComponent(lista, valor, indice, seleccionada, foco);
            if (valor instanceof Categoria c) {
                setIcon(Iconos.punto(Estilo.colorDe(c)));
                setIconTextGap(UIScale.scale(8));
                if (indice < 0) {
                    setText(c.getNombre()); // dentro de la casilla del desplegable: solo el nombre
                    return this;
                }
                String tipo = switch (c.getTipo()) {
                    case ASIGNATURA -> c.isIntegrada() ? "elígela después" : "";
                    case PERSONAL -> "personal";
                    case ARCHIVO -> "tipo de archivo";
                    case DUPLICADO -> "copias repetidas";
                    case DEJAR -> "no mover";
                };
                String gris = Estilo.hex(seleccionada ? lista.getSelectionForeground() : Estilo.gris());
                setText(tipo.isEmpty() ? c.getNombre()
                        : "<html>" + Estilo.html(c.getNombre()) + "&nbsp;&nbsp;<font color='" + gris + "'>" + tipo + "</font></html>");
            }
            return this;
        }
    }
}
