package ordenalo.ui;

import ordenalo.modelo.Accion;
import ordenalo.modelo.Categoria;
import ordenalo.modelo.Propuesta;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/** Las filas de la tabla «Revisa la propuesta»: una por elemento. */
final class ModeloTabla extends AbstractTableModel {

    static final int SEL = 0;
    static final int ELEMENTO = 1;
    static final int DESTINO = 2;
    static final int SUBCARPETA = 3;
    static final int MOTIVO = 4;
    static final int CONFIANZA = 5;

    private static final String[] COLUMNAS = {"", "Elemento", "Va a", "Subcarpeta", "Por qué", "Confianza"};

    private List<Propuesta> filas = List.of();
    private final Runnable alEditar;

    ModeloTabla(Runnable alEditar) {
        this.alEditar = alEditar;
    }

    void setFilas(List<Propuesta> filas) {
        this.filas = List.copyOf(filas);
        fireTableDataChanged();
    }

    /** Repinta todas las filas sin perder la selección. */
    void refrescar() {
        if (!filas.isEmpty()) {
            fireTableRowsUpdated(0, filas.size() - 1);
        }
    }

    Propuesta fila(int indice) {
        return filas.get(indice);
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
    public String getColumnName(int columna) {
        return COLUMNAS[columna];
    }

    @Override
    public Class<?> getColumnClass(int columna) {
        return columna == SEL ? Boolean.class : Object.class;
    }

    @Override
    public Object getValueAt(int fila, int columna) {
        Propuesta p = filas.get(fila);
        return switch (columna) {
            case SEL -> p.seleccionada();
            case SUBCARPETA -> p.getSubcarpeta();
            default -> p;
        };
    }

    @Override
    public boolean isCellEditable(int fila, int columna) {
        Propuesta p = filas.get(fila);
        return switch (columna) {
            case SEL -> p.getAccion() == Accion.MOVER;
            case DESTINO -> true;
            case SUBCARPETA -> p.getAccion() == Accion.MOVER && p.categoria().getTipo() == Categoria.Tipo.ASIGNATURA;
            default -> false;
        };
    }

    @Override
    public void setValueAt(Object valor, int fila, int columna) {
        Propuesta p = filas.get(fila);
        switch (columna) {
            case SEL -> p.setSeleccionUsuario(Boolean.TRUE.equals(valor));
            case DESTINO -> {
                if (!(valor instanceof Categoria c) || c == p.categoria()) {
                    return;
                }
                p.elegirCategoria(c);
            }
            case SUBCARPETA -> {
                String texto = valor == null ? "" : valor.toString().trim();
                if (texto.equals(p.getSubcarpeta())) {
                    return;
                }
                p.setSubcarpetaUsuario(texto);
            }
            default -> {
                return;
            }
        }
        alEditar.run();
    }
}
