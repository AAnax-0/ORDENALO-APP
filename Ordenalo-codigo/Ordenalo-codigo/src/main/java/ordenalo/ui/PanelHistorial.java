package ordenalo.ui;

import com.formdev.flatlaf.util.UIScale;
import ordenalo.motor.Historial.Ejecucion;
import ordenalo.motor.Historial.Operacion;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Pestaña «Historial»: cada ordenación hecha, con su botón de deshacer. */
final class PanelHistorial extends JPanel {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy  HH:mm");
    private static final DateTimeFormatter CORTA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final VentanaPrincipal ventana;
    private final Modelo modelo = new Modelo();
    private final JTable tabla = new JTable(modelo);
    private final JButton btnDeshacer = Estilo.principal("Deshacer esta ordenación");
    private final JButton btnDetalle = new JButton("Ver qué se movió");

    PanelHistorial(VentanaPrincipal ventana) {
        super(new BorderLayout(0, 12));
        this.ventana = ventana;
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        Tarjeta t = new Tarjeta(new BorderLayout(0, 12));
        JPanel titulos = new JPanel(new GridLayout(2, 1, 0, 2));
        titulos.setOpaque(false);
        titulos.add(Estilo.titulo("Historial"));
        titulos.add(Estilo.subtitulo("Cada vez que mueves cosas queda apuntado aquí. Puedes deshacer cualquier ordenación, aunque hayas cerrado el programa."));
        t.add(titulos, BorderLayout.NORTH);

        tabla.setRowHeight(UIScale.scale(32));
        tabla.setFillsViewportHeight(true);
        tabla.setShowVerticalLines(false);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.getTableHeader().setReorderingAllowed(false);
        DefaultTableCellRenderer render = new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                super.getTableCellRendererComponent(t, v, s, false, r, c);
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                Ejecucion e = modelo.fila(t.convertRowIndexToModel(r));
                setFont(t.getFont());
                if (!s && (e.getDeshecha() != null || !e.esDeEsteEquipo())) {
                    setForeground(Estilo.gris());
                }
                if (c == 1) {
                    setToolTipText(e.getOrigen() == null ? null : e.getOrigen().toString());
                }
                return this;
            }
        };
        tabla.setDefaultRenderer(Object.class, render);
        tabla.getColumnModel().getColumn(0).setPreferredWidth(UIScale.scale(150));
        tabla.getColumnModel().getColumn(1).setPreferredWidth(UIScale.scale(420));
        tabla.getColumnModel().getColumn(2).setPreferredWidth(UIScale.scale(90));
        tabla.getColumnModel().getColumn(3).setPreferredWidth(UIScale.scale(140));
        tabla.getColumnModel().getColumn(4).setPreferredWidth(UIScale.scale(220));
        tabla.getSelectionModel().addListSelectionListener(e -> actualizarBotones());
        tabla.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && tabla.getSelectedRow() >= 0) {
                    verDetalle();
                }
            }
        });
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        t.add(scroll, BorderLayout.CENTER);
        add(t, BorderLayout.CENTER);

        JPanel barra = new JPanel(new BorderLayout());
        barra.setOpaque(false);
        JPanel izquierda = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        izquierda.setOpaque(false);
        JButton carpeta = new JButton("Abrir carpeta del historial");
        carpeta.addActionListener(e -> Dialogos.abrir(this, ventana.historial().getCarpeta()));
        izquierda.add(carpeta);
        JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        derecha.setOpaque(false);
        btnDetalle.addActionListener(e -> verDetalle());
        btnDeshacer.addActionListener(e -> {
            Ejecucion sel = seleccionada();
            if (sel != null) {
                ventana.deshacer(sel);
            }
        });
        derecha.add(btnDetalle);
        derecha.add(btnDeshacer);
        barra.add(izquierda, BorderLayout.WEST);
        barra.add(derecha, BorderLayout.EAST);
        add(barra, BorderLayout.SOUTH);
        refrescar();
    }

    void refrescar() {
        modelo.filas = ventana.historial().listar();
        modelo.fireTableDataChanged();
        if (!modelo.filas.isEmpty()) {
            tabla.setRowSelectionInterval(0, 0);
        }
        actualizarBotones();
    }

    private Ejecucion seleccionada() {
        int fila = tabla.getSelectedRow();
        return fila < 0 ? null : modelo.fila(tabla.convertRowIndexToModel(fila));
    }

    private void actualizarBotones() {
        Ejecucion e = seleccionada();
        btnDeshacer.setEnabled(e != null && e.sePuedeDeshacer());
        btnDetalle.setEnabled(e != null);
    }

    private void verDetalle() {
        Ejecucion e = seleccionada();
        if (e == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        int movidos = 0;
        for (Operacion op : e.getOperaciones()) {
            if (op.tipo() != Operacion.Tipo.MOVIDO) {
                continue;
            }
            movidos++;
            sb.append(op.a().getFileName()).append('\n')
                    .append("    de  ").append(carpeta(op.a())).append('\n')
                    .append("    a   ").append(carpeta(op.b())).append("\n\n");
        }
        if (movidos == 0) {
            sb.append("En esta ordenación no se movió nada.");
        }
        JTextArea area = new JTextArea(sb.toString().strip());
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, tabla.getFont().getSize()));
        area.setCaretPosition(0);
        area.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(UIScale.scale(760), UIScale.scale(440)));
        javax.swing.JOptionPane.showMessageDialog(this, scroll,
                "Ordenación del " + FECHA.format(e.getFecha()) + " · " + movidos + " movimientos",
                javax.swing.JOptionPane.PLAIN_MESSAGE);
    }

    private static String carpeta(Path p) {
        return p.getParent() == null ? p.toString() : p.getParent().toString();
    }

    private static final class Modelo extends AbstractTableModel {
        private static final String[] COLUMNAS = {"Fecha", "Carpeta ordenada", "Elementos", "Ordenador", "Estado"};
        private List<Ejecucion> filas = List.of();

        Ejecucion fila(int i) {
            return filas.get(i);
        }

        @Override
        public int getRowCount() {
            return filas.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNAS.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLUMNAS[c];
        }

        @Override
        public Object getValueAt(int r, int c) {
            Ejecucion e = filas.get(r);
            return switch (c) {
                case 0 -> FECHA.format(e.getFecha());
                case 1 -> e.getOrigen() == null ? "" : PanelOrdenar.nombreBonito(e.getOrigen()) + "   ·   " + e.getOrigen();
                case 2 -> e.getElementos();
                case 3 -> e.esDeEsteEquipo() ? e.getEquipo() + " (este)" : e.getEquipo();
                default -> estado(e);
            };
        }

        private static String estado(Ejecucion e) {
            if (e.getDeshecha() != null) {
                return "Deshecha el " + CORTA.format(e.getDeshecha());
            }
            if (e.movimientos() == 0) {
                return "No se movió nada";
            }
            if (!e.isTerminada()) {
                return "Se cortó a medias (se puede deshacer)";
            }
            return e.getErrores() > 0 ? "Hecha (" + e.getErrores() + " sin mover)" : "Hecha";
        }
    }
}
