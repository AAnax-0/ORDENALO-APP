package ordenalo.ui;

import com.formdev.flatlaf.util.UIScale;
import ordenalo.modelo.Plan;
import ordenalo.modelo.Propuesta;
import ordenalo.motor.Archivos;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** El árbol «Así quedará»: las carpetas de destino con lo que va a llegar a cada una. */
final class ArbolDestino {

    private ArbolDestino() {
    }

    static final class NodoCarpeta {
        final Path ruta;
        final String nombre;
        final boolean nueva;
        final boolean raiz;
        int elementos;

        NodoCarpeta(Path ruta, String nombre, boolean nueva, boolean raiz) {
            this.ruta = ruta;
            this.nombre = nombre;
            this.nueva = nueva;
            this.raiz = raiz;
        }
    }

    record NodoElemento(Propuesta propuesta) {
    }

    /**
     * @param rutas se rellena con la posición de cada propuesta en el árbol (para seleccionarla)
     */
    static DefaultMutableTreeNode construir(Plan plan, Map<Propuesta, TreePath> rutas) {
        DefaultMutableTreeNode raiz = new DefaultMutableTreeNode("Así quedará");
        if (plan == null) {
            return raiz;
        }
        Map<Path, DefaultMutableTreeNode> nodos = new HashMap<>();
        for (Propuesta p : plan.getPropuestas()) {
            if (!p.seleccionada() || p.getDestino() == null) {
                continue;
            }
            Path destino = p.getDestino();
            Path base = base(plan, destino);
            DefaultMutableTreeNode actual = nodos.get(base);
            if (actual == null) {
                String nombre = base.getFileName() == null ? base.toString() : base.getFileName().toString();
                actual = new DefaultMutableTreeNode(new NodoCarpeta(base, nombre, !Archivos.existe(base), true));
                raiz.add(actual);
                nodos.put(base, actual);
            }
            Path acumulado = base;
            for (Path parte : base.relativize(destino.getParent())) {
                acumulado = acumulado.resolve(parte);
                DefaultMutableTreeNode hijo = nodos.get(acumulado);
                if (hijo == null) {
                    hijo = new DefaultMutableTreeNode(new NodoCarpeta(acumulado, parte.toString(), !Archivos.existe(acumulado), false));
                    actual.add(hijo);
                    nodos.put(acumulado, hijo);
                }
                actual = hijo;
            }
            actual.add(new DefaultMutableTreeNode(new NodoElemento(p), false));
        }
        ordenarYContar(raiz);
        Enumeration<TreeNode> todos = raiz.depthFirstEnumeration();
        while (todos.hasMoreElements()) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) todos.nextElement();
            if (n.getUserObject() instanceof NodoElemento e) {
                rutas.put(e.propuesta(), new TreePath(n.getPath()));
            }
        }
        return raiz;
    }

    private static Path base(Plan plan, Path destino) {
        if (destino.startsWith(plan.getDestinoClase())) {
            return plan.getDestinoClase();
        }
        if (destino.startsWith(plan.getDestinoOtros())) {
            return plan.getDestinoOtros();
        }
        return destino.getParent();
    }

    /** Carpetas primero y por orden alfabético; cuenta cuántas cosas llegan a cada carpeta. */
    private static int ordenarYContar(DefaultMutableTreeNode nodo) {
        List<DefaultMutableTreeNode> hijos = new ArrayList<>();
        for (int i = 0; i < nodo.getChildCount(); i++) {
            hijos.add((DefaultMutableTreeNode) nodo.getChildAt(i));
        }
        hijos.sort(Comparator.comparing((DefaultMutableTreeNode n) -> n.getUserObject() instanceof NodoElemento)
                .thenComparing(n -> textoDe(n).toLowerCase(Locale.ROOT)));
        nodo.removeAllChildren();
        int total = 0;
        for (DefaultMutableTreeNode h : hijos) {
            nodo.add(h);
            total += h.getUserObject() instanceof NodoElemento ? 1 : ordenarYContar(h);
        }
        if (nodo.getUserObject() instanceof NodoCarpeta c) {
            c.elementos = total;
        }
        return total;
    }

    private static String textoDe(DefaultMutableTreeNode n) {
        Object o = n.getUserObject();
        if (o instanceof NodoCarpeta c) {
            return c.nombre;
        }
        if (o instanceof NodoElemento e) {
            return e.propuesta().getDestino().getFileName().toString();
        }
        return String.valueOf(o);
    }

    /** Pinta carpetas (con cuántas cosas llegan y si son nuevas) y elementos (con su icono). */
    static final class Render extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree arbol, Object valor, boolean seleccionado, boolean expandido,
                                                      boolean hoja, int fila, boolean foco) {
            super.getTreeCellRendererComponent(arbol, "", seleccionado, expandido, hoja, fila, foco);
            setIconTextGap(UIScale.scale(6));
            Object o = ((DefaultMutableTreeNode) valor).getUserObject();
            String gris = Estilo.hex(seleccionado ? getTextSelectionColor() : Estilo.gris());
            if (o instanceof NodoCarpeta c) {
                setIcon(Iconos.carpeta());
                String nombre = Estilo.html(c.nombre);
                String extra = c.elementos + (c.nueva ? " · nueva" : "");
                setText("<html>" + (c.raiz ? "<b>" + nombre + "</b>" : nombre)
                        + "&nbsp;&nbsp;<font color='" + gris + "'>" + extra + "</font></html>");
                setToolTipText(c.ruta.toString());
            } else if (o instanceof NodoElemento e) {
                Propuesta p = e.propuesta();
                setIcon(Iconos.deElemento(p.getElemento()));
                String final_ = p.getDestino().getFileName().toString();
                boolean renombrado = !final_.equals(p.getElemento().getNombre());
                setText(renombrado
                        ? "<html>" + Estilo.html(final_) + "&nbsp;&nbsp;<font color='" + gris + "'>renombrado</font></html>"
                        : final_);
                setToolTipText(p.motivo());
            } else {
                setText(String.valueOf(o));
            }
            return this;
        }
    }
}
