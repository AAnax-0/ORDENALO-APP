package ordenalo.ui;

import ordenalo.motor.Archivos;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/** Mensajes, confirmaciones y la ventanita de progreso. */
final class Dialogos {

    private static final String TITULO = "Ordénalo";

    private Dialogos() {
    }

    static void aviso(Component padre, String mensaje) {
        JOptionPane.showMessageDialog(padre, mensaje, TITULO, JOptionPane.WARNING_MESSAGE);
    }

    static void error(Component padre, String mensaje, Throwable causa) {
        String detalle = causa == null ? "" : "\n\n" + (causa instanceof Exception ex ? Archivos.explicar(ex) : causa.toString());
        JOptionPane.showMessageDialog(padre, mensaje + detalle, TITULO, JOptionPane.ERROR_MESSAGE);
    }

    static boolean confirmar(Component padre, String titulo, String mensaje, String botonSi) {
        Object[] opciones = {botonSi, "Cancelar"};
        int r = JOptionPane.showOptionDialog(padre, mensaje, titulo, JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, opciones, opciones[0]);
        return r == 0;
    }

    /** Resumen al terminar, con una lista de detalles (errores, avisos) y un botón opcional. */
    static void informe(Component padre, String titulo, String mensajeHtml, List<String> detalles,
                        String botonExtra, Runnable accionExtra) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(new JLabel("<html>" + mensajeHtml + "</html>"), BorderLayout.NORTH);
        if (!detalles.isEmpty()) {
            JTextArea area = new JTextArea(String.join("\n", detalles));
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            JScrollPane scroll = new JScrollPane(area);
            scroll.setPreferredSize(new Dimension(540, Math.min(240, 22 * detalles.size() + 24)));
            panel.add(scroll, BorderLayout.CENTER);
        }
        Object[] opciones = botonExtra == null ? new Object[]{"Vale"} : new Object[]{botonExtra, "Vale"};
        int r = JOptionPane.showOptionDialog(padre, panel, titulo, JOptionPane.DEFAULT_OPTION,
                detalles.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE,
                null, opciones, opciones[0]);
        if (botonExtra != null && r == 0 && accionExtra != null) {
            accionExtra.run();
        }
    }

    static void abrir(Component padre, Path ruta) {
        try {
            Desktop.getDesktop().open(ruta.toFile());
        } catch (Exception e) {
            aviso(padre, "No he podido abrir:\n" + ruta);
        }
    }

    // ─── Trabajo largo con barra de progreso ─────────────────────────────

    interface Tarea<T> {
        T ejecutar(ordenalo.motor.Ejecutor.Progreso progreso) throws Exception;
    }

    /** Ejecuta la tarea en segundo plano mostrando una ventanita con la barra de progreso. */
    static <T> void conProgreso(Component padre, String titulo, Tarea<T> tarea, Consumer<T> alTerminar,
                                Consumer<Throwable> alFallar) {
        JDialog dialogo = new JDialog(SwingUtilities.getWindowAncestor(padre), titulo, Dialog.ModalityType.APPLICATION_MODAL);
        dialogo.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JProgressBar barra = new JProgressBar();
        barra.setIndeterminate(true);
        JLabel texto = new JLabel("Preparando…");
        JPanel contenido = new JPanel(new BorderLayout(0, 10));
        contenido.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        contenido.add(texto, BorderLayout.NORTH);
        contenido.add(barra, BorderLayout.CENTER);
        contenido.setPreferredSize(new Dimension(440, 90));
        dialogo.setContentPane(contenido);
        dialogo.pack();
        dialogo.setLocationRelativeTo(padre);

        SwingWorker<T, Object[]> trabajo = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return tarea.ejecutar((hechos, total, que) -> publish(new Object[]{hechos, total, que}));
            }

            @Override
            protected void process(List<Object[]> avances) {
                Object[] ultimo = avances.get(avances.size() - 1);
                int hechos = (int) ultimo[0];
                int total = (int) ultimo[1];
                barra.setIndeterminate(false);
                barra.setMaximum(Math.max(1, total));
                barra.setValue(hechos);
                String que = (String) ultimo[2];
                texto.setText(que.isEmpty() ? "Terminando…" : (Math.min(hechos + 1, total)) + " de " + total + ": " + que);
            }

            @Override
            protected void done() {
                dialogo.dispose();
                try {
                    alTerminar.accept(get());
                } catch (ExecutionException e) {
                    alFallar.accept(e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        trabajo.execute();
        dialogo.setVisible(true); // se cierra solo en done()
    }
}
