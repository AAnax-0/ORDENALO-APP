package ordenalo.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import ordenalo.modelo.Accion;
import ordenalo.modelo.Categoria;
import ordenalo.modelo.Confianza;
import ordenalo.modelo.Plan;
import ordenalo.modelo.Propuesta;
import ordenalo.motor.CarpetasSistema;
import ordenalo.motor.Config;
import ordenalo.motor.Ejecutor;
import ordenalo.motor.Escaner;
import ordenalo.motor.Planificador;
import ordenalo.motor.Reglas;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Pestaña principal: elegir carpeta, revisar la propuesta y mover. */
final class PanelOrdenar extends JPanel {

    private static final String VACIO = "vacio";
    private static final String CARGANDO = "cargando";
    private static final String RESULTADO = "resultado";

    private final VentanaPrincipal ventana;
    private Plan plan;
    private Planificador planificador;
    private Path origen;
    private Map<Propuesta, TreePath> rutasArbol = new HashMap<>();
    private SwingWorker<Plan, String> analisis;
    private boolean ocupado;
    private boolean hayDeshacible;

    private final JTextField campoOrigen = campoRuta("Elige qué quieres ordenar");
    private final JTextField campoClase = campoRuta("");
    private final JTextField campoOtros = campoRuta("");
    private final JCheckBox chkOtros = new JCheckBox("Ordenar también lo que no es de clase (fotos, PDF, instaladores…)");
    private final JCheckBox chkDuplicados = new JCheckBox("Buscar copias repetidas");
    private final JButton btnAnalizar = Estilo.principal("Analizar");
    private final List<JButton> botonesOrigen = new ArrayList<>();

    private final ModeloTabla modelo = new ModeloTabla(this::alEditar);
    private final JTable tabla = new JTable(modelo);
    private final TableRowSorter<ModeloTabla> ordenador = new TableRowSorter<>(modelo);
    private final JComboBox<String> filtro = new JComboBox<>(new String[]{"Todo", "Lo que se mueve", "Lo que se queda", "Lo dudoso"});
    private final JTree arbol = new JTree(new DefaultMutableTreeNode());
    private final CardLayout cartasArbol = new CardLayout();
    private final JPanel contenidoArbol = new JPanel(cartasArbol);

    private final JLabel lblResumen = new JLabel(" ");
    private final JButton btnDeshacer = new JButton("Deshacer la última");
    private final JButton btnAplicar = Estilo.principal("Mover");

    private final CardLayout cartas = new CardLayout();
    private final JPanel centro = new JPanel(cartas);
    private final JLabel lblCargando = new JLabel("Analizando…", SwingConstants.CENTER);
    private final JLabel lblVacioTitulo = new JLabel("", SwingConstants.CENTER);
    private final JLabel lblVacioTexto = new JLabel("", SwingConstants.CENTER);

    PanelOrdenar(VentanaPrincipal ventana) {
        super(new BorderLayout(0, 12));
        this.ventana = ventana;
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        add(crearPasos(), BorderLayout.NORTH);
        add(crearCentro(), BorderLayout.CENTER);
        add(crearPie(), BorderLayout.SOUTH);
        cargarConfig();
        actualizarDeshacer();
    }

    // ─── Construcción ────────────────────────────────────────────────────

    private JPanel crearPasos() {
        Tarjeta t = new Tarjeta(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 0, 5, 16);
        g.anchor = GridBagConstraints.WEST;

        g.gridx = 0;
        g.gridy = 0;
        t.add(paso(1, "¿Qué quieres ordenar?"), g);

        JPanel origenes = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        origenes.setOpaque(false);
        botonOrigen(origenes, "Escritorio", CarpetasSistema::escritorio);
        botonOrigen(origenes, "Descargas", CarpetasSistema::descargas);
        botonOrigen(origenes, "Documentos", CarpetasSistema::documentos);
        JButton otra = new JButton("Otra carpeta…");
        otra.addActionListener(e -> elegirOtraCarpeta());
        botonesOrigen.add(otra);
        origenes.add(otra);
        JPanel filaOrigen = new JPanel(new BorderLayout(10, 0));
        filaOrigen.setOpaque(false);
        filaOrigen.add(origenes, BorderLayout.WEST);
        filaOrigen.add(campoOrigen, BorderLayout.CENTER);
        g.gridx = 1;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(5, 0, 5, 0);
        t.add(filaOrigen, g);

        g.gridx = 0;
        g.gridy = 1;
        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;
        g.insets = new Insets(5, 0, 5, 16);
        t.add(paso(2, "¿Dónde lo guardo?"), g);

        JPanel destinos = new JPanel(new GridLayout(1, 2, 16, 0));
        destinos.setOpaque(false);
        destinos.add(filaDestino("Clase", campoClase, true,
                "Aquí van las asignaturas: una carpeta por asignatura y, dentro, por tareas"));
        destinos.add(filaDestino("Lo demás", campoOtros, false,
                "Aquí va lo que no es de clase: imágenes, PDF, instaladores, copias repetidas…"));
        g.gridx = 1;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(5, 0, 5, 0);
        t.add(destinos, g);

        JPanel opciones = new JPanel(new BorderLayout());
        opciones.setOpaque(false);
        JPanel checks = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        checks.setOpaque(false);
        chkOtros.setOpaque(false);
        chkDuplicados.setOpaque(false);
        checks.add(chkOtros);
        checks.add(Box.createHorizontalStrut(18));
        checks.add(chkDuplicados);
        opciones.add(checks, BorderLayout.CENTER);
        opciones.add(btnAnalizar, BorderLayout.EAST);
        g.gridy = 2;
        g.insets = new Insets(8, 0, 0, 0);
        t.add(opciones, g);
        return t;
    }

    private static JLabel paso(int n, String texto) {
        JLabel l = new JLabel(texto, Iconos.numero(n), SwingConstants.LEFT);
        l.setIconTextGap(UIScale.scale(10));
        Estilo.claseEstilo(l, "h4");
        return l;
    }

    private void botonOrigen(JPanel panel, String nombre, Supplier<Path> carpeta) {
        JButton b = new JButton(nombre);
        b.addActionListener(e -> elegirOrigen(carpeta.get()));
        botonesOrigen.add(b);
        panel.add(b);
    }

    private JPanel filaDestino(String etiqueta, JTextField campo, boolean clase, String ayuda) {
        JPanel fila = new JPanel(new BorderLayout(8, 0));
        fila.setOpaque(false);
        JLabel l = new JLabel(etiqueta);
        l.setToolTipText(ayuda);
        l.setForeground(Estilo.gris());
        campo.setToolTipText(ayuda);
        JButton cambiar = new JButton("Cambiar…");
        cambiar.addActionListener(e -> cambiarDestino(clase));
        fila.add(l, BorderLayout.WEST);
        fila.add(campo, BorderLayout.CENTER);
        fila.add(cambiar, BorderLayout.EAST);
        return fila;
    }

    /** Pone una ruta en un campo, mostrando el final (lo importante) y la ruta entera al pasar el ratón. */
    private static void ponerRuta(JTextField campo, Path ruta) {
        campo.setText(ruta.toString());
        campo.setCaretPosition(campo.getText().length());
        campo.setToolTipText(ruta.toString());
    }

    private static JTextField campoRuta(String ayuda) {
        JTextField campo = new JTextField();
        campo.setEditable(false);
        campo.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, ayuda);
        return campo;
    }

    private JPanel crearCentro() {
        centro.setOpaque(false);
        centro.add(crearVacio(), VACIO);
        centro.add(crearCargando(), CARGANDO);
        centro.add(crearResultado(), RESULTADO);
        return centro;
    }

    private JPanel crearVacio() {
        Tarjeta t = new Tarjeta(new GridBagLayout());
        JPanel caja = new JPanel();
        caja.setOpaque(false);
        caja.setLayout(new BoxLayout(caja, BoxLayout.Y_AXIS));
        JLabel icono = new JLabel(new ImageIcon(Iconos.dibujarApp(UIScale.scale(72))));
        icono.setAlignmentX(CENTER_ALIGNMENT);
        Estilo.claseEstilo(lblVacioTitulo, "h2");
        lblVacioTitulo.setAlignmentX(CENTER_ALIGNMENT);
        lblVacioTexto.setAlignmentX(CENTER_ALIGNMENT);
        lblVacioTexto.setForeground(Estilo.gris());
        caja.add(icono);
        caja.add(Box.createVerticalStrut(16));
        caja.add(lblVacioTitulo);
        caja.add(Box.createVerticalStrut(10));
        caja.add(lblVacioTexto);
        t.add(caja);
        return t;
    }

    private JPanel crearCargando() {
        Tarjeta t = new Tarjeta(new GridBagLayout());
        JPanel caja = new JPanel(new BorderLayout(0, 12));
        caja.setOpaque(false);
        JProgressBar barra = new JProgressBar();
        barra.setIndeterminate(true);
        barra.setPreferredSize(new Dimension(UIScale.scale(260), UIScale.scale(6)));
        lblCargando.setForeground(Estilo.gris());
        caja.add(barra, BorderLayout.CENTER);
        caja.add(lblCargando, BorderLayout.SOUTH);
        t.add(caja);
        return t;
    }

    private JSplitPane crearResultado() {
        // Tabla
        Tarjeta tarjetaTabla = new Tarjeta(new BorderLayout(0, 10));
        JPanel cabecera = new JPanel(new BorderLayout(12, 0));
        cabecera.setOpaque(false);
        JPanel titulos = new JPanel(new GridLayout(2, 1, 0, 2));
        titulos.setOpaque(false);
        titulos.add(Estilo.titulo("Revisa la propuesta"));
        titulos.add(Estilo.subtitulo("Cambia «Va a» si algo no cuadra. Lo dudoso solo se mueve si lo marcas."));
        cabecera.add(titulos, BorderLayout.CENTER);
        JPanel panelFiltro = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        panelFiltro.setOpaque(false);
        panelFiltro.add(new JLabel("Mostrar"));
        panelFiltro.add(filtro);
        cabecera.add(panelFiltro, BorderLayout.EAST);
        tarjetaTabla.add(cabecera, BorderLayout.NORTH);
        configurarTabla();
        JScrollPane scrollTabla = new JScrollPane(tabla);
        scrollTabla.setBorder(BorderFactory.createEmptyBorder());
        tarjetaTabla.add(scrollTabla, BorderLayout.CENTER);

        // Árbol
        Tarjeta tarjetaArbol = new Tarjeta(new BorderLayout(0, 10));
        JPanel titulosArbol = new JPanel(new GridLayout(2, 1, 0, 2));
        titulosArbol.setOpaque(false);
        titulosArbol.add(Estilo.titulo("Así quedará"));
        titulosArbol.add(Estilo.subtitulo("Lo que se va a mover, ya en su sitio"));
        tarjetaArbol.add(titulosArbol, BorderLayout.NORTH);
        arbol.setRootVisible(false);
        arbol.setShowsRootHandles(true);
        arbol.setOpaque(false);
        arbol.setCellRenderer(new ArbolDestino.Render());
        javax.swing.ToolTipManager.sharedInstance().registerComponent(arbol);
        JScrollPane scrollArbol = new JScrollPane(arbol);
        scrollArbol.setBorder(BorderFactory.createEmptyBorder());
        scrollArbol.setOpaque(false);
        scrollArbol.getViewport().setOpaque(false);
        contenidoArbol.setOpaque(false);
        contenidoArbol.add(scrollArbol, "arbol");
        JLabel nada = new JLabel("<html><div style='text-align:center'>No hay nada marcado para mover.<br>"
                + "Marca algo en la tabla y aquí verás dónde acaba.</div></html>", SwingConstants.CENTER);
        nada.setForeground(Estilo.gris());
        contenidoArbol.add(nada, "nada");
        tarjetaArbol.add(contenidoArbol, BorderLayout.CENTER);
        tarjetaArbol.setMinimumSize(new Dimension(UIScale.scale(260), 0));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tarjetaTabla, tarjetaArbol);
        split.setResizeWeight(0.7);
        split.setContinuousLayout(true);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setOpaque(false);
        split.setDividerSize(UIScale.scale(12));
        // La primera vez que tiene tamaño: 70 % para la tabla y 30 % para el árbol.
        split.addComponentListener(new java.awt.event.ComponentAdapter() {
            private boolean colocado;

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (!colocado && split.getWidth() > 0) {
                    colocado = true;
                    split.setDividerLocation((int) (split.getWidth() * 0.7));
                }
            }
        });
        return split;
    }

    private void configurarTabla() {
        tabla.setRowSorter(ordenador);
        tabla.setFillsViewportHeight(true);
        tabla.setRowHeight(UIScale.scale(30));
        tabla.setShowVerticalLines(false);
        tabla.setShowHorizontalLines(true);
        tabla.getTableHeader().setReorderingAllowed(false);
        if (tabla.getTableHeader().getDefaultRenderer() instanceof JLabel cabecera) {
            cabecera.setHorizontalAlignment(SwingConstants.LEADING);
        }
        tabla.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        tabla.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        columna(ModeloTabla.SEL, 36, 36, 36);
        columna(ModeloTabla.ELEMENTO, 150, 260, Integer.MAX_VALUE);
        columna(ModeloTabla.DESTINO, 140, 210, Integer.MAX_VALUE);
        columna(ModeloTabla.SUBCARPETA, 80, 125, Integer.MAX_VALUE);
        columna(ModeloTabla.MOTIVO, 140, 340, Integer.MAX_VALUE);
        columna(ModeloTabla.CONFIANZA, 90, 96, 120);

        tabla.getColumnModel().getColumn(ModeloTabla.SEL).setCellRenderer(new Celdas.Marca());
        tabla.getColumnModel().getColumn(ModeloTabla.ELEMENTO).setCellRenderer(new Celdas.NombreElemento());
        tabla.getColumnModel().getColumn(ModeloTabla.DESTINO).setCellRenderer(new Celdas.Destino());
        tabla.getColumnModel().getColumn(ModeloTabla.SUBCARPETA).setCellRenderer(new Celdas.Subcarpeta());
        tabla.getColumnModel().getColumn(ModeloTabla.MOTIVO).setCellRenderer(new Celdas.Motivo());
        tabla.getColumnModel().getColumn(ModeloTabla.CONFIANZA).setCellRenderer(new Celdas.Pildora());
        ponerEditorDestino();

        Comparator<Object> porNombre = Comparator.comparing(o -> ((Propuesta) o).getElemento().getNombre().toLowerCase(Locale.ROOT));
        ordenador.setComparator(ModeloTabla.ELEMENTO, porNombre);
        ordenador.setComparator(ModeloTabla.DESTINO, Comparator.comparing(o -> ((Propuesta) o).categoria().getNombre().toLowerCase(Locale.ROOT)));
        ordenador.setComparator(ModeloTabla.MOTIVO, Comparator.comparing(o -> ((Propuesta) o).motivo()));
        ordenador.setComparator(ModeloTabla.CONFIANZA, Comparator.comparing(o -> ((Propuesta) o).getConfianza()));
        ordenador.setSortable(ModeloTabla.SEL, false);

        filtro.addActionListener(e -> aplicarFiltro());
        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                sincronizarArbol();
            }
        });
        tabla.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                menuContextual(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                menuContextual(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int fila = tabla.rowAtPoint(e.getPoint());
                int col = tabla.columnAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && fila >= 0 && tabla.convertColumnIndexToModel(col) == ModeloTabla.ELEMENTO) {
                    Dialogos.abrir(PanelOrdenar.this, modelo.fila(tabla.convertRowIndexToModel(fila)).getElemento().getRuta());
                }
            }
        });
    }

    private void columna(int indice, int minimo, int preferido, int maximo) {
        TableColumn c = tabla.getColumnModel().getColumn(indice);
        c.setMinWidth(UIScale.scale(minimo));
        c.setPreferredWidth(UIScale.scale(preferido));
        c.setMaxWidth(maximo == Integer.MAX_VALUE ? maximo : UIScale.scale(maximo));
    }

    private void ponerEditorDestino() {
        tabla.getColumnModel().getColumn(ModeloTabla.DESTINO)
                .setCellEditor(new Celdas.EditorDestino(ventana.reglas().paraElegir()));
    }

    private JPanel crearPie() {
        JPanel pie = new JPanel(new BorderLayout(12, 0));
        pie.setOpaque(false);
        lblResumen.setForeground(Estilo.gris());
        pie.add(lblResumen, BorderLayout.CENTER);
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botones.setOpaque(false);
        btnDeshacer.setToolTipText("Devuelve todo lo de la última ordenación a donde estaba");
        botones.add(btnDeshacer);
        botones.add(btnAplicar);
        pie.add(botones, BorderLayout.EAST);
        btnAnalizar.addActionListener(e -> analizar());
        btnAplicar.addActionListener(e -> aplicar());
        btnDeshacer.addActionListener(e -> ventana.deshacerUltima());
        return pie;
    }

    private void cargarConfig() {
        Config c = ventana.config();
        ponerRuta(campoClase, c.getDestinoClase());
        ponerRuta(campoOtros, c.getDestinoOtros());
        chkOtros.setSelected(c.isMoverOtros());
        chkDuplicados.setSelected(c.isBuscarDuplicados());
        c.getUltimoOrigen().filter(Files::isDirectory).ifPresent(p -> {
            origen = p;
            ponerRuta(campoOrigen, p);
        });
        chkOtros.addActionListener(e -> {
            c.setMoverOtros(chkOtros.isSelected());
            c.guardar();
            if (plan != null) {
                plan.setMoverOtros(chkOtros.isSelected());
                alEditar();
            }
        });
        chkDuplicados.addActionListener(e -> {
            c.setBuscarDuplicados(chkDuplicados.isSelected());
            c.guardar();
            if (plan != null) {
                analizar();
            }
        });
        mostrarVacio();
    }

    // ─── Acciones ────────────────────────────────────────────────────────

    void elegirOrigen(Path carpeta) {
        if (ocupado) {
            return;
        }
        if (carpeta == null || !Files.isDirectory(carpeta)) {
            Dialogos.aviso(this, "No encuentro la carpeta:\n" + carpeta);
            return;
        }
        if (CarpetasSistema.esPeligrosa(carpeta)) {
            Dialogos.aviso(this, "Mejor elige una carpeta concreta, como el Escritorio o Descargas.\n\n«" + carpeta
                    + "» es una carpeta del sistema o tu carpeta de usuario entera,\ny ordenarla podría mover cosas que no se deben tocar.");
            return;
        }
        origen = carpeta.toAbsolutePath().normalize();
        ponerRuta(campoOrigen, origen);
        ventana.config().setUltimoOrigen(origen);
        ventana.config().guardar();
        analizar();
    }

    private void elegirOtraCarpeta() {
        JFileChooser selector = new JFileChooser(origen != null ? origen.toFile() : CarpetasSistema.inicio().toFile());
        selector.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        selector.setDialogTitle("¿Qué carpeta quieres ordenar?");
        if (selector.showDialog(this, "Ordenar esta") == JFileChooser.APPROVE_OPTION) {
            elegirOrigen(selector.getSelectedFile().toPath());
        }
    }

    private void cambiarDestino(boolean clase) {
        Config c = ventana.config();
        Path actual = clase ? c.getDestinoClase() : c.getDestinoOtros();
        Path inicial = Files.isDirectory(actual) ? actual : actual.getParent();
        JFileChooser selector = new JFileChooser(inicial != null ? inicial.toFile() : null);
        selector.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        selector.setDialogTitle(clase ? "¿Dónde guardo las asignaturas?" : "¿Dónde guardo lo que no es de clase?");
        if (selector.showDialog(this, "Usar esta carpeta") != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path elegida = selector.getSelectedFile().toPath().toAbsolutePath().normalize();
        if (CarpetasSistema.esPeligrosa(elegida)) {
            Dialogos.aviso(this, "Esa carpeta es del sistema o tu carpeta de usuario entera. Elige o crea una carpeta normal.");
            return;
        }
        if (clase) {
            c.setDestinoClase(elegida);
            ponerRuta(campoClase, elegida);
        } else {
            c.setDestinoOtros(elegida);
            ponerRuta(campoOtros, elegida);
        }
        c.guardar();
        if (plan != null) {
            analizar();
        }
    }

    void analizar() {
        if (origen == null) {
            Dialogos.aviso(this, "Primero elige qué quieres ordenar: el Escritorio, Descargas…");
            return;
        }
        if (analisis != null) {
            analisis.cancel(true);
        }
        Path carpeta = origen;
        Config c = ventana.config();
        Reglas reglas = ventana.reglas();
        Path clase = c.getDestinoClase();
        Path otros = c.getDestinoOtros();
        boolean moverOtros = chkOtros.isSelected();
        boolean duplicados = chkDuplicados.isSelected();
        lblCargando.setText("Mirando qué hay en «" + nombreBonito(carpeta) + "»…");
        cartas.show(centro, CARGANDO);
        setOcupado(true);

        SwingWorker<Plan, String> trabajo = new SwingWorker<>() {
            private Planificador nuevo;

            @Override
            protected Plan doInBackground() throws Exception {
                Consumer<String> aviso = this::publish;
                Escaner.Resultado r = Escaner.escanear(carpeta, c.exclusionesPara(carpeta), aviso);
                publish("Clasificando " + r.elementos().size() + " elementos…");
                nuevo = new Planificador(reglas);
                return nuevo.crear(carpeta, r, clase, otros, moverOtros, duplicados);
            }

            @Override
            protected void process(List<String> mensajes) {
                lblCargando.setText(mensajes.get(mensajes.size() - 1));
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                setOcupado(false);
                try {
                    planificador = nuevo;
                    mostrarPlan(get());
                } catch (ExecutionException ex) {
                    plan = null;
                    mostrarVacio();
                    Dialogos.error(PanelOrdenar.this, "No he podido mirar la carpeta.", ex.getCause());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        analisis = trabajo;
        trabajo.execute();
    }

    private void mostrarPlan(Plan nuevo) {
        this.plan = nuevo;
        List<Categoria> orden = ventana.reglas().paraElegir();
        List<Propuesta> filas = new ArrayList<>(nuevo.getPropuestas());
        filas.sort(Comparator.comparing((Propuesta p) -> p.getAccion() == Accion.DEJAR)
                .thenComparingInt(p -> orden.indexOf(p.categoria()))
                .thenComparing(p -> p.getElemento().getNombre().toLowerCase(Locale.ROOT)));
        modelo.setFilas(filas);
        if (filas.isEmpty()) {
            lblVacioTitulo.setText("Aquí no hay nada que ordenar");
            lblVacioTexto.setText("«" + nombreBonito(nuevo.getOrigen()) + "» ya está limpia. ¡Bien!");
            cartas.show(centro, VACIO);
        } else {
            cartas.show(centro, RESULTADO);
        }
        refrescar();
    }

    private void mostrarVacio() {
        if (origen != null) {
            lblVacioTitulo.setText("Listo para ordenar «" + nombreBonito(origen) + "»");
            lblVacioTexto.setText("<html><div style='text-align:center'>Pulsa <b>Analizar</b> y verás a dónde iría cada cosa y por qué.<br>"
                    + "No se mueve nada hasta que pulses <b>Mover</b>, y siempre puedes deshacerlo.</div></html>");
        } else {
            lblVacioTitulo.setText("Tu escritorio, ordenado en un momento");
            lblVacioTexto.setText("<html><div style='text-align:center'>1. Elige qué quieres ordenar: el Escritorio, Descargas…<br>"
                    + "2. Revisa a dónde iría cada cosa (por asignatura y por tarea).<br>"
                    + "3. Pulsa <b>Mover</b>. Si no te gusta, <b>Deshacer</b> lo deja todo como estaba.</div></html>");
        }
        cartas.show(centro, VACIO);
        refrescar();
    }

    /** Después de cambiar algo en la tabla, se recalculan los destinos. */
    private void alEditar() {
        if (plan == null || planificador == null) {
            return;
        }
        planificador.recalcular(plan);
        refrescar();
    }

    private void refrescar() {
        modelo.refrescar();
        aplicarFiltro();
        reconstruirArbol();
        actualizarResumen();
        actualizarBotones();
    }

    private void aplicarFiltro() {
        int opcion = filtro.getSelectedIndex();
        if (opcion <= 0) {
            ordenador.setRowFilter(null);
            return;
        }
        ordenador.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends ModeloTabla, ? extends Integer> entrada) {
                Propuesta p = modelo.fila(entrada.getIdentifier());
                return switch (opcion) {
                    case 1 -> p.seleccionada();
                    case 2 -> !p.seleccionada();
                    default -> p.getConfianza() == Confianza.BAJA && !p.isElegidaPorUsuario();
                };
            }
        });
    }

    private void reconstruirArbol() {
        Map<Propuesta, TreePath> rutas = new HashMap<>();
        DefaultMutableTreeNode raiz = ArbolDestino.construir(plan, rutas);
        arbol.setModel(new DefaultTreeModel(raiz));
        for (int i = 0; i < arbol.getRowCount(); i++) {
            arbol.expandRow(i);
        }
        cartasArbol.show(contenidoArbol, raiz.getChildCount() == 0 ? "nada" : "arbol");
        rutasArbol = rutas;
        sincronizarArbol();
    }

    private void sincronizarArbol() {
        int fila = tabla.getSelectedRow();
        if (fila < 0 || plan == null) {
            arbol.clearSelection();
            return;
        }
        TreePath ruta = rutasArbol.get(modelo.fila(tabla.convertRowIndexToModel(fila)));
        if (ruta == null) {
            arbol.clearSelection();
        } else {
            arbol.setSelectionPath(ruta);
            arbol.scrollPathToVisible(ruta);
        }
    }

    private void actualizarResumen() {
        if (plan == null) {
            lblResumen.setText(" ");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(plan.total()).append(plan.total() == 1 ? " elemento" : " elementos")
                .append(" · ").append(plan.seMueven()).append(" se mueven · ")
                .append(plan.seQuedan()).append(" se quedan");
        if (plan.copias() > 0) {
            sb.append(" · ").append(plan.copias()).append(plan.copias() == 1 ? " copia repetida" : " copias repetidas");
        }
        if (plan.dudosasSinMarcar() > 0) {
            sb.append(" · ").append(plan.dudosasSinMarcar()).append(plan.dudosasSinMarcar() == 1 ? " dudoso sin marcar" : " dudosos sin marcar");
        }
        if (plan.getIgnorados() > 0) {
            sb.append(" · ").append(plan.getIgnorados()).append(" ocultos o descargándose (no se tocan)");
        }
        lblResumen.setText(sb.toString());
    }

    private void actualizarBotones() {
        int n = plan == null ? 0 : plan.seMueven();
        btnAplicar.setText(n == 0 ? "Mover" : n == 1 ? "Mover 1 elemento" : "Mover " + n + " elementos");
        btnAplicar.setEnabled(!ocupado && n > 0);
        btnAnalizar.setEnabled(!ocupado);
        botonesOrigen.forEach(b -> b.setEnabled(!ocupado));
        btnDeshacer.setEnabled(!ocupado && hayDeshacible);
    }

    void actualizarDeshacer() {
        hayDeshacible = ventana.historial().ultimaDeshacible().isPresent();
        actualizarBotones();
    }

    private void setOcupado(boolean valor) {
        ocupado = valor;
        tabla.setEnabled(!valor);
        setCursor(valor ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : null);
        actualizarBotones();
    }

    private void aplicar() {
        if (plan == null || ocupado || plan.seMueven() == 0) {
            return;
        }
        if (tabla.isEditing()) {
            tabla.getCellEditor().stopCellEditing();
        }
        int n = plan.seMueven();
        if (!Dialogos.confirmar(this, "¿Muevo los archivos?",
                "Voy a mover " + n + (n == 1 ? " elemento" : " elementos") + " de «" + nombreBonito(plan.getOrigen()) + "».\n"
                        + "No se borra nada y podrás deshacerlo cuando quieras.", "Mover")) {
            return;
        }
        Plan aMover = plan;
        Dialogos.conProgreso(this, "Ordenando…",
                progreso -> new Ejecutor().aplicar(aMover, ventana.historial(), progreso),
                informe -> {
                    ventana.historialCambiado();
                    String mensaje = informe.getErrores().isEmpty()
                            ? "<b>¡Hecho!</b> He movido " + informe.getCorrectos() + (informe.getCorrectos() == 1 ? " elemento." : " elementos.")
                            + "<br>Si algo no te gusta, pulsa <b>Deshacer la última</b>."
                            : "He movido " + informe.getCorrectos() + " de " + n + ". Esto no se ha podido mover:";
                    analizar();
                    Dialogos.informe(this, "Ordenado", mensaje, informe.getErrores(), "Abrir la carpeta de clase",
                            () -> Dialogos.abrir(this, aMover.getDestinoClase()));
                },
                error -> {
                    ventana.historialCambiado();
                    analizar();
                    Dialogos.error(this, "Algo ha fallado al mover. Lo que se haya movido se puede deshacer desde «Historial».", error);
                });
    }

    /** Clic derecho: abrir, mandar a otra categoría, marcar o desmarcar varias filas a la vez. */
    private void menuContextual(MouseEvent e) {
        if (!e.isPopupTrigger() || plan == null || ocupado) {
            return;
        }
        int fila = tabla.rowAtPoint(e.getPoint());
        if (fila < 0) {
            return;
        }
        if (!tabla.isRowSelected(fila)) {
            tabla.setRowSelectionInterval(fila, fila);
        }
        List<Propuesta> elegidas = new ArrayList<>();
        for (int f : tabla.getSelectedRows()) {
            elegidas.add(modelo.fila(tabla.convertRowIndexToModel(f)));
        }
        JPopupMenu menu = new JPopupMenu();
        if (elegidas.size() == 1) {
            Path ruta = elegidas.get(0).getElemento().getRuta();
            menu.add(opcion("Abrir", () -> Dialogos.abrir(this, ruta)));
            menu.add(opcion("Abrir la carpeta donde está", () -> Dialogos.abrir(this, ruta.getParent())));
            menu.addSeparator();
        }
        JMenu mandar = new JMenu(elegidas.size() == 1 ? "Mandar a" : "Mandar los " + elegidas.size() + " a");
        for (Categoria c : ventana.reglas().paraElegir()) {
            JMenuItem item = opcion(c.getNombre(), () -> {
                elegidas.forEach(p -> p.elegirCategoria(c));
                alEditar();
            });
            item.setIcon(Iconos.punto(Estilo.colorDe(c)));
            mandar.add(item);
        }
        menu.add(mandar);
        menu.add(opcion("Mover", () -> {
            elegidas.stream().filter(p -> p.getAccion() == Accion.MOVER).forEach(p -> p.setSeleccionUsuario(true));
            alEditar();
        }));
        menu.add(opcion("No mover", () -> {
            elegidas.forEach(p -> p.setSeleccionUsuario(false));
            alEditar();
        }));
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private static JMenuItem opcion(String texto, Runnable accion) {
        JMenuItem item = new JMenuItem(texto);
        item.addActionListener(e -> accion.run());
        return item;
    }

    // ─── Avisos desde la ventana ─────────────────────────────────────────

    void reglasCambiadas() {
        ponerEditorDestino();
        if (plan != null) {
            analizar();
        }
    }

    void reanalizar() {
        if (plan != null && origen != null) {
            analizar();
        }
    }

    static String nombreBonito(Path carpeta) {
        if (carpeta == null) {
            return "";
        }
        if (carpeta.equals(CarpetasSistema.escritorio())) {
            return "Escritorio";
        }
        if (carpeta.equals(CarpetasSistema.descargas())) {
            return "Descargas";
        }
        if (carpeta.equals(CarpetasSistema.documentos())) {
            return "Documentos";
        }
        return carpeta.getFileName() == null ? carpeta.toString() : carpeta.getFileName().toString();
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(UIScale.scale(900), UIScale.scale(560));
    }
}
