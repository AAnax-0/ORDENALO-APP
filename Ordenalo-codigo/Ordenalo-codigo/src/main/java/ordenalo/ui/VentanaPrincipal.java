package ordenalo.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.util.UIScale;
import ordenalo.App;
import ordenalo.motor.Config;
import ordenalo.motor.Ejecutor;
import ordenalo.motor.Historial;
import ordenalo.motor.Historial.Ejecucion;
import ordenalo.motor.Reglas;
import ordenalo.motor.Reparador;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/** La ventana de Ordénalo, con sus tres pestañas. */
public final class VentanaPrincipal extends JFrame {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM 'a las' HH:mm");

    private final Config config;
    private final Historial historial;
    private Reglas reglas;
    private final JTabbedPane pestanas = new JTabbedPane();
    private final PanelOrdenar panelOrdenar;
    private final PanelReglas panelReglas;
    private final PanelHistorial panelHistorial;

    public static void lanzar() {
        Config config = Config.cargar();
        SwingUtilities.invokeLater(() -> {
            Estilo.aplicarTema(config.isTemaOscuro());
            VentanaPrincipal ventana = new VentanaPrincipal(config);
            ventana.setVisible(true);
            SwingUtilities.invokeLater(ventana::revisarArreglos);
        });
    }

    /** Si una versión anterior movió algo del sistema por error, ofrece devolverlo a su sitio. */
    void revisarArreglos() {
        java.util.List<Reparador.Arreglo> arreglos = Reparador.buscar(historial);
        if (arreglos.isEmpty()) {
            return;
        }
        java.util.List<String> nombres = arreglos.stream().map(a -> "«" + a.original().getFileName() + "»").distinct().toList();
        Path carpeta = arreglos.get(0).original().getParent();
        String mensaje = "La versión anterior de Ordénalo movió por error "
                + (arreglos.size() == 1 ? "un enlace que Windows usa" : arreglos.size() + " enlaces que Windows usa")
                + " por dentro:\n" + String.join(", ", nombres) + "\n\n"
                + "No son archivos tuyos (están ocultos), pero tienen que estar en «"
                + (carpeta.getFileName() == null ? carpeta : carpeta.getFileName()) + "». ¿Los devuelvo a su sitio?\n"
                + "No se toca nada más.";
        if (!Dialogos.confirmar(this, "Arreglo", mensaje, "Devolver")) {
            return;
        }
        java.util.List<String> errores = Reparador.reparar(arreglos, historial);
        historialCambiado();
        Dialogos.informe(this, "Arreglo", errores.isEmpty()
                        ? "<b>Listo.</b> Ya están otra vez en su sitio."
                        : "No he podido devolverlos todos:", errores, null, null);
    }

    VentanaPrincipal(Config config) {
        super(App.NOMBRE);
        this.config = config;
        this.historial = new Historial(config.carpetaHistorial());
        String avisoReglas = null;
        Reglas cargadas;
        try {
            cargadas = Reglas.cargar(config.archivoReglas());
        } catch (IOException | RuntimeException e) {
            cargadas = Reglas.porDefecto();
            avisoReglas = "No he podido leer tus reglas (" + e.getMessage() + ").\nDe momento uso las de fábrica.";
        }
        this.reglas = cargadas;

        setIconImages(Iconos.iconosVentana());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cerrar();
            }
        });

        panelOrdenar = new PanelOrdenar(this);
        panelReglas = new PanelReglas(this);
        panelHistorial = new PanelHistorial(this);
        pestanas.addTab("Ordenar", panelOrdenar);
        pestanas.addTab("Reglas", panelReglas);
        pestanas.addTab("Historial", panelHistorial);
        pestanas.putClientProperty(FlatClientProperties.TABBED_PANE_LEADING_COMPONENT, cabecera());
        pestanas.putClientProperty(FlatClientProperties.TABBED_PANE_TRAILING_COMPONENT, botonesDerecha());
        pestanas.putClientProperty(FlatClientProperties.TABBED_PANE_MINIMUM_TAB_WIDTH, UIScale.scale(110));
        pestanas.addChangeListener(e -> {
            if (pestanas.getSelectedComponent() == panelHistorial) {
                panelHistorial.refrescar();
            }
        });
        setContentPane(pestanas);

        Rectangle pantalla = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(UIScale.scale(1280), pantalla.width - 40), Math.min(UIScale.scale(820), pantalla.height - 40));
        setMinimumSize(new Dimension(Math.min(UIScale.scale(960), pantalla.width), Math.min(UIScale.scale(640), pantalla.height)));
        setLocationRelativeTo(null);

        if (avisoReglas != null) {
            String aviso = avisoReglas;
            SwingUtilities.invokeLater(() -> Dialogos.aviso(this, aviso));
        }
    }

    private JPanel cabecera() {
        JPanel p = new JPanel(new GridBagLayout()); // centra el logo en vertical
        p.setOpaque(false);
        JLabel logo = new JLabel(App.NOMBRE, new ImageIcon(Iconos.dibujarApp(UIScale.scale(24))), JLabel.LEFT);
        logo.setIconTextGap(UIScale.scale(10));
        Estilo.claseEstilo(logo, "h3");
        logo.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 28));
        p.add(logo);
        return p;
    }

    private JPanel botonesDerecha() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        p.setOpaque(false);
        JToggleButton tema = new JToggleButton(Iconos.luna());
        tema.setSelected(config.isTemaOscuro());
        tema.setToolTipText("Modo oscuro");
        tema.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        tema.addActionListener(e -> {
            config.setTemaOscuro(tema.isSelected());
            config.guardar();
            Estilo.aplicarTema(tema.isSelected());
            Iconos.limpiarCache();
            FlatLaf.updateUI();
        });
        JButton ayuda = new JButton();
        ayuda.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_HELP);
        ayuda.setToolTipText("Cómo funciona");
        ayuda.addActionListener(e -> ayuda());
        p.add(tema);
        p.add(ayuda);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        JPanel centrado = new JPanel(new GridBagLayout());
        centrado.setOpaque(false);
        centrado.add(p);
        JPanel derecha = new JPanel(new java.awt.BorderLayout());
        derecha.setOpaque(false);
        derecha.add(centrado, java.awt.BorderLayout.EAST);
        return derecha;
    }

    private void ayuda() {
        String texto = "<html><div style='width:420px'>"
                + "<h3>Cómo funciona</h3>"
                + "<p><b>1. Elige qué ordenar</b> (Escritorio, Descargas…). Ordénalo mira lo que hay suelto: "
                + "las carpetas se mueven enteras, sin deshacerlas.</p><br>"
                + "<p><b>2. Revisa la propuesta.</b> Cada cosa va a su asignatura según su nombre y lo que lleva dentro "
                + "(«Tarea3_SI» → Sistemas Informáticos › Tarea 3). Lo dudoso no se mueve si no lo marcas.</p><br>"
                + "<p><b>3. Pulsa Mover.</b> Nunca se borra ni se sobrescribe nada: si un nombre choca, se renombra. "
                + "Todo queda en el Historial y se puede deshacer.</p><br>"
                + "<p><b>Trucos:</b> clic derecho en la tabla para mandar varias cosas a la vez a otro sitio; "
                + "doble clic en un archivo para abrirlo y ver qué es.</p><br>"
                + "<p>En <b>Reglas</b> puedes añadir palabras (el nombre de tu profe, de un proyecto…) para que acierte más.</p><br>"
                + "<p style='color:gray'>Tus reglas y el historial se guardan en:<br>" + Estilo.html(config.getCarpetaDatos().toString()) + "</p>"
                + "</div></html>";
        JOptionPane.showMessageDialog(this, texto, App.NOMBRE + " " + App.VERSION, JOptionPane.INFORMATION_MESSAGE,
                new ImageIcon(Iconos.dibujarApp(UIScale.scale(56))));
    }

    // ─── Lo que comparten las pestañas ───────────────────────────────────

    Config config() {
        return config;
    }

    Reglas reglas() {
        return reglas;
    }

    Historial historial() {
        return historial;
    }

    void reglasGuardadas(Reglas nuevas) {
        this.reglas = nuevas;
        panelOrdenar.reglasCambiadas();
    }

    void marcarReglasSinGuardar(boolean sinGuardar) {
        int i = pestanas.indexOfComponent(panelReglas);
        if (i >= 0) {
            pestanas.setTitleAt(i, sinGuardar ? "Reglas •" : "Reglas");
        }
    }

    void historialCambiado() {
        panelHistorial.refrescar();
        panelOrdenar.actualizarDeshacer();
    }

    void deshacerUltima() {
        Optional<Ejecucion> ultima = historial.ultimaDeshacible();
        if (ultima.isEmpty()) {
            Dialogos.aviso(this, "No hay nada que deshacer.");
            return;
        }
        deshacer(ultima.get());
    }

    void deshacer(Ejecucion e) {
        String donde = e.getOrigen() == null ? "" : " de «" + PanelOrdenar.nombreBonito(e.getOrigen()) + "»";
        String otroEquipo = e.esDeEsteEquipo() ? ""
                : "\n\nOjo: se hizo en otro ordenador («" + e.getEquipo() + "»). Solo se devolverá lo que exista aquí.";
        if (!Dialogos.confirmar(this, "Deshacer",
                "¿Devuelvo a su sitio lo que se ordenó el " + FECHA.format(e.getFecha()) + donde + "?\n"
                        + e.getElementos() + (e.getElementos() == 1 ? " elemento." : " elementos.") + otroEquipo,
                "Deshacer")) {
            return;
        }
        Dialogos.conProgreso(this, "Deshaciendo…",
                progreso -> new Ejecutor().deshacer(e, historial, progreso),
                informe -> {
                    historialCambiado();
                    panelOrdenar.reanalizar();
                    java.util.List<String> detalles = new java.util.ArrayList<>(informe.getAvisos());
                    detalles.addAll(informe.getErrores());
                    String mensaje = informe.getErrores().isEmpty()
                            ? "<b>Listo.</b> Todo ha vuelto a donde estaba (" + informe.getCorrectos()
                            + (informe.getCorrectos() == 1 ? " elemento)." : " elementos).")
                            : "He devuelto " + informe.getCorrectos() + " a su sitio, pero hay cosas que no he podido:";
                    Dialogos.informe(this, "Deshecho", mensaje, detalles, null, null);
                },
                error -> {
                    historialCambiado();
                    Dialogos.error(this, "Algo ha fallado al deshacer.", error);
                });
    }

    private void cerrar() {
        if (panelReglas.hayCambios()) {
            Object[] opciones = {"Guardar", "No guardar", "Cancelar"};
            int r = JOptionPane.showOptionDialog(this, "Has cambiado las reglas y no las has guardado. ¿Las guardo?",
                    App.NOMBRE, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opciones, opciones[0]);
            if (r == 2 || r == JOptionPane.CLOSED_OPTION) {
                return;
            }
            if (r == 0 && !panelReglas.guardar()) {
                return;
            }
        }
        config.guardar();
        dispose();
        System.exit(0);
    }
}
