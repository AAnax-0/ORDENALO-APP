package ordenalo.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import ordenalo.modelo.Categoria;
import ordenalo.modelo.Categoria.Tipo;
import ordenalo.modelo.Clasificacion;
import ordenalo.modelo.Elemento;
import ordenalo.motor.Clasificador;
import ordenalo.motor.DetectorGrupos;
import ordenalo.motor.Reglas;
import ordenalo.motor.Texto;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pestaña «Reglas»: qué palabras y extensiones mandan cada cosa a cada sitio. */
final class PanelReglas extends JPanel {

    private static final String[] PALETA = {"#2f6fde", "#0e9f6e", "#d97706", "#db2777", "#7c3aed", "#0891b2",
            "#4f46e5", "#e11d48", "#65a30d", "#a16207", "#0f766e", "#9333ea"};

    private final VentanaPrincipal ventana;
    private Reglas trabajo;
    private boolean cambios;
    private boolean cargando;

    private final DefaultListModel<Categoria> modeloLista = new DefaultListModel<>();
    private final JList<Categoria> lista = new JList<>(modeloLista);
    private final JTextField campoNombre = new JTextField();
    private final JComboBox<Tipo> comboTipo = new JComboBox<>(new Tipo[]{Tipo.ASIGNATURA, Tipo.PERSONAL, Tipo.ARCHIVO});
    private final JTextField campoCarpeta = new JTextField();
    private final JTextArea areaPalabras = new JTextArea(4, 30);
    private final JTextField campoExtensiones = new JTextField();
    private final JButton btnColor = new JButton("Cambiar color");
    private final JCheckBox chkSoloTipo = new JCheckBox("La extensión manda aunque el nombre diga otra cosa");
    private final JTextField campoPrueba = new JTextField();
    private final JLabel lblPrueba = new JLabel(" ");
    private final JButton btnGuardar = Estilo.principal("Guardar reglas");
    private final JButton btnDescartar = new JButton("Descartar cambios");
    private final JButton btnEliminar = new JButton("Quitar");
    private final JButton btnSubir = new JButton("Subir");
    private final JButton btnBajar = new JButton("Bajar");
    private final List<JComponent> camposFormulario;

    PanelReglas(VentanaPrincipal ventana) {
        super(new BorderLayout(14, 12));
        this.ventana = ventana;
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        camposFormulario = List.of(campoNombre, comboTipo, campoCarpeta, areaPalabras, campoExtensiones, btnColor, chkSoloTipo);
        add(crearLista(), BorderLayout.WEST);
        add(crearFormulario(), BorderLayout.CENTER);
        add(crearBarra(), BorderLayout.SOUTH);
        cargar(ventana.reglas().copia());
        btnGuardar.setEnabled(false);
        btnDescartar.setEnabled(false);
    }

    // ─── Construcción ────────────────────────────────────────────────────

    private JPanel crearLista() {
        Tarjeta t = new Tarjeta(new BorderLayout(0, 10));
        t.setPreferredSize(new Dimension(UIScale.scale(330), 0));
        JPanel titulos = new JPanel(new GridLayout(2, 1, 0, 2));
        titulos.setOpaque(false);
        titulos.add(Estilo.titulo("Categorías"));
        titulos.add(Estilo.subtitulo("A qué sitios puede ir cada cosa"));
        t.add(titulos, BorderLayout.NORTH);

        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lista.setCellRenderer(new RenderLista());
        lista.setFixedCellHeight(UIScale.scale(30));
        lista.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                mostrar(lista.getSelectedValue());
            }
        });
        JScrollPane scroll = new JScrollPane(lista);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        t.add(scroll, BorderLayout.CENTER);

        JButton nuevaAsignatura = new JButton("+ Asignatura");
        nuevaAsignatura.addActionListener(e -> nueva(Tipo.ASIGNATURA));
        JButton nuevoTipo = new JButton("+ Tipo de archivo");
        nuevoTipo.addActionListener(e -> nueva(Tipo.ARCHIVO));
        btnEliminar.addActionListener(e -> eliminar());
        btnSubir.addActionListener(e -> mover(-1));
        btnBajar.addActionListener(e -> mover(1));
        btnSubir.setToolTipText("El orden importa en los tipos de archivo: gana el primero que encaja");
        btnBajar.setToolTipText(btnSubir.getToolTipText());
        JPanel botones = new JPanel(new GridLayout(2, 1, 0, 6));
        botones.setOpaque(false);
        JPanel fila1 = new JPanel(new GridLayout(1, 2, 6, 0));
        fila1.setOpaque(false);
        fila1.add(nuevaAsignatura);
        fila1.add(nuevoTipo);
        JPanel fila2 = new JPanel(new GridLayout(1, 3, 6, 0));
        fila2.setOpaque(false);
        fila2.add(btnSubir);
        fila2.add(btnBajar);
        fila2.add(btnEliminar);
        botones.add(fila1);
        botones.add(fila2);
        t.add(botones, BorderLayout.SOUTH);
        return t;
    }

    private JPanel crearFormulario() {
        Tarjeta t = new Tarjeta(new BorderLayout(0, 14));
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.NORTHWEST;
        g.insets = new Insets(6, 0, 6, 14);

        comboTipo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof Tipo tipo) {
                    setText(switch (tipo) {
                        case ASIGNATURA -> "Asignatura: va a la carpeta de clase, ordenada por tareas";
                        case PERSONAL -> "Personal: algo tuyo que no es de clase";
                        case ARCHIVO -> "Tipo de archivo: se reconoce por la extensión";
                        default -> tipo.etiqueta();
                    });
                }
                return this;
            }
        });
        areaPalabras.setLineWrap(true);
        areaPalabras.setWrapStyleWord(true);
        areaPalabras.setRows(4);
        campoNombre.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Por ejemplo: Programación");
        campoCarpeta.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Nombre de la carpeta que se crea");
        campoExtensiones.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Por ejemplo: java, class, jar");
        chkSoloTipo.setOpaque(false);

        int fila = 0;
        fila = filaFormulario(form, g, fila, "Nombre", campoNombre, null);
        fila = filaFormulario(form, g, fila, "Qué es", comboTipo, null);
        fila = filaFormulario(form, g, fila, "Carpeta", campoCarpeta, "Si ya tienes una carpeta parecida (como «PROG»), se usa esa.");
        JScrollPane scrollPalabras = new JScrollPane(areaPalabras);
        Dimension alto = scrollPalabras.getPreferredSize();
        scrollPalabras.setMinimumSize(new Dimension(UIScale.scale(200), alto.height));
        fila = filaFormulario(form, g, fila, "Palabras clave", scrollPalabras,
                "Separadas por comas. Si el nombre contiene alguna, va aquí (dan igual mayúsculas, tildes y plurales).");
        fila = filaFormulario(form, g, fila, "Extensiones", campoExtensiones,
                "En las asignaturas suman puntos; en los tipos de archivo deciden.");
        JPanel filaColor = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filaColor.setOpaque(false);
        filaColor.add(btnColor);
        fila = filaFormulario(form, g, fila, "Color", filaColor, null);
        g.gridx = 1;
        g.gridy = fila++;
        g.fill = GridBagConstraints.NONE;
        form.add(chkSoloTipo, g);
        g.gridy = fila;
        g.weighty = 1;
        form.add(new JLabel(), g);
        t.add(form, BorderLayout.CENTER);

        // Probar
        JPanel prueba = new JPanel(new BorderLayout(0, 8));
        prueba.setOpaque(false);
        prueba.add(new JSeparator(), BorderLayout.NORTH);
        JPanel filaPrueba = new JPanel(new BorderLayout(12, 0));
        filaPrueba.setOpaque(false);
        JLabel etiqueta = new JLabel("Prueba un nombre");
        Estilo.claseEstilo(etiqueta, "h4");
        filaPrueba.add(etiqueta, BorderLayout.WEST);
        campoPrueba.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Escribe un nombre de archivo, como Tarea3_SI.docx o ejercicio5.java");
        campoPrueba.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        filaPrueba.add(campoPrueba, BorderLayout.CENTER);
        prueba.add(filaPrueba, BorderLayout.CENTER);
        lblPrueba.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 0));
        prueba.add(lblPrueba, BorderLayout.SOUTH);
        t.add(prueba, BorderLayout.SOUTH);

        alCambiarTexto(campoNombre, this::guardarFormulario);
        alCambiarTexto(campoCarpeta, this::guardarFormulario);
        alCambiarTexto(areaPalabras, this::guardarFormulario);
        alCambiarTexto(campoExtensiones, this::guardarFormulario);
        alCambiarTexto(campoPrueba, this::probar);
        comboTipo.addActionListener(e -> guardarFormulario());
        chkSoloTipo.addActionListener(e -> guardarFormulario());
        btnColor.addActionListener(e -> elegirColor());
        return t;
    }

    private static int filaFormulario(JPanel form, GridBagConstraints g, int fila, String etiqueta, JComponent campo, String ayuda) {
        g.gridx = 0;
        g.gridy = fila;
        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;
        g.insets = new Insets(ayuda == null ? 6 : 6, 0, ayuda == null ? 6 : 0, 14);
        JLabel l = new JLabel(etiqueta);
        l.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(5), 0, 0, 0));
        form.add(l, g);
        g.gridx = 1;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        form.add(campo, g);
        if (ayuda == null) {
            return fila + 1;
        }
        g.gridy = fila + 1;
        g.insets = new Insets(3, 0, 8, 0);
        form.add(Estilo.ayuda(ayuda), g);
        return fila + 2;
    }

    private JPanel crearBarra() {
        JPanel barra = new JPanel(new BorderLayout());
        barra.setOpaque(false);
        JPanel izquierda = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        izquierda.setOpaque(false);
        JButton fabrica = new JButton("Restaurar las de fábrica");
        fabrica.addActionListener(e -> restaurar());
        JButton datos = new JButton("Abrir carpeta de datos");
        datos.setToolTipText("Donde se guardan las reglas (reglas.ini) y el historial");
        datos.addActionListener(e -> Dialogos.abrir(this, ventana.config().getCarpetaDatos()));
        izquierda.add(fabrica);
        izquierda.add(datos);
        JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        derecha.setOpaque(false);
        btnDescartar.addActionListener(e -> {
            cargar(ventana.reglas().copia());
            marcarCambios(false);
        });
        btnGuardar.addActionListener(e -> guardar());
        derecha.add(btnDescartar);
        derecha.add(btnGuardar);
        barra.add(izquierda, BorderLayout.WEST);
        barra.add(derecha, BorderLayout.EAST);
        return barra;
    }

    // ─── Datos ───────────────────────────────────────────────────────────

    private void cargar(Reglas reglas) {
        trabajo = reglas;
        int antes = Math.max(0, lista.getSelectedIndex());
        modeloLista.clear();
        for (Categoria c : trabajo.getCategorias()) {
            modeloLista.addElement(c);
        }
        if (!modeloLista.isEmpty()) {
            lista.setSelectedIndex(Math.min(antes, modeloLista.size() - 1));
        } else {
            mostrar(null);
        }
        probar();
    }

    private void mostrar(Categoria c) {
        cargando = true;
        boolean hay = c != null;
        camposFormulario.forEach(campo -> campo.setEnabled(hay));
        btnEliminar.setEnabled(hay);
        btnSubir.setEnabled(hay && lista.getSelectedIndex() > 0);
        btnBajar.setEnabled(hay && lista.getSelectedIndex() < modeloLista.size() - 1);
        campoNombre.setText(hay ? c.getNombre() : "");
        comboTipo.setSelectedItem(hay ? c.getTipo() : Tipo.ASIGNATURA);
        campoCarpeta.setText(hay ? c.getCarpeta() : "");
        areaPalabras.setText(hay ? String.join(", ", c.getPalabras()) : "");
        areaPalabras.setCaretPosition(0);
        campoExtensiones.setText(hay ? String.join(", ", c.getExtensiones()) : "");
        btnColor.setIcon(hay ? Iconos.punto(Estilo.colorDe(c), 14) : null);
        chkSoloTipo.setSelected(hay && c.isSoloPorTipo());
        chkSoloTipo.setEnabled(hay && c.getTipo() == Tipo.ARCHIVO);
        cargando = false;
    }

    private void guardarFormulario() {
        Categoria c = lista.getSelectedValue();
        if (cargando || c == null) {
            return;
        }
        String nombreAnterior = c.getNombre();
        c.setNombre(campoNombre.getText());
        if (!c.getNombre().equals(nombreAnterior) && Texto.mismoNombre(campoCarpeta.getText(), nombreAnterior)) {
            cargando = true;
            campoCarpeta.setText(c.getNombre()); // la carpeta sigue al nombre si no se había cambiado
            cargando = false;
        }
        c.setTipo((Tipo) comboTipo.getSelectedItem());
        c.setCarpeta(campoCarpeta.getText());
        c.setPalabras(Texto.separarLista(areaPalabras.getText()));
        c.setExtensiones(Texto.separarLista(campoExtensiones.getText().replace(' ', ',')));
        chkSoloTipo.setEnabled(c.getTipo() == Tipo.ARCHIVO);
        c.setSoloPorTipo(chkSoloTipo.isSelected() && c.getTipo() == Tipo.ARCHIVO);
        lista.repaint();
        marcarCambios(true);
        probar();
    }

    private void nueva(Tipo tipo) {
        String base = tipo == Tipo.ASIGNATURA ? "Nueva asignatura" : "Nuevo tipo de archivo";
        Categoria c = new Categoria(nombreLibre(base), tipo);
        c.setColor(PALETA[trabajo.getCategorias().size() % PALETA.length]);
        List<Categoria> cats = trabajo.getCategorias();
        int posicion = cats.size();
        if (tipo == Tipo.ASIGNATURA) {
            for (int i = cats.size() - 1; i >= 0; i--) {
                if (cats.get(i).getTipo() == Tipo.ASIGNATURA) {
                    posicion = i + 1;
                    break;
                }
            }
        }
        cats.add(posicion, c);
        modeloLista.add(posicion, c);
        lista.setSelectedIndex(posicion);
        lista.ensureIndexIsVisible(posicion);
        campoNombre.requestFocusInWindow();
        campoNombre.selectAll();
        marcarCambios(true);
    }

    private String nombreLibre(String base) {
        Set<String> usados = new HashSet<>();
        trabajo.getCategorias().forEach(c -> usados.add(c.getNombre().toLowerCase(Locale.ROOT)));
        String nombre = base;
        for (int i = 2; usados.contains(nombre.toLowerCase(Locale.ROOT)); i++) {
            nombre = base + " " + i;
        }
        return nombre;
    }

    private void eliminar() {
        Categoria c = lista.getSelectedValue();
        if (c == null || !Dialogos.confirmar(this, "Quitar categoría",
                "¿Quito «" + c.getNombre() + "»?\nLo que iba aquí se clasificará de otra forma. Tus carpetas no se tocan.", "Quitar")) {
            return;
        }
        int i = lista.getSelectedIndex();
        trabajo.getCategorias().remove(c);
        modeloLista.remove(i);
        if (!modeloLista.isEmpty()) {
            lista.setSelectedIndex(Math.min(i, modeloLista.size() - 1));
        }
        marcarCambios(true);
        probar();
    }

    private void mover(int salto) {
        int i = lista.getSelectedIndex();
        int j = i + salto;
        if (i < 0 || j < 0 || j >= modeloLista.size()) {
            return;
        }
        List<Categoria> cats = trabajo.getCategorias();
        Categoria c = cats.remove(i);
        cats.add(j, c);
        modeloLista.remove(i);
        modeloLista.add(j, c);
        lista.setSelectedIndex(j);
        marcarCambios(true);
        probar();
    }

    private void elegirColor() {
        Categoria c = lista.getSelectedValue();
        if (c == null) {
            return;
        }
        Color nuevo = JColorChooser.showDialog(this, "Color de «" + c.getNombre() + "»", Estilo.colorDe(c));
        if (nuevo != null) {
            c.setColor(Estilo.hex(nuevo));
            btnColor.setIcon(Iconos.punto(nuevo, 14));
            lista.repaint();
            marcarCambios(true);
        }
    }

    private void restaurar() {
        if (Dialogos.confirmar(this, "Reglas de fábrica",
                "¿Vuelvo a las reglas con las que viene el programa?\nPerderás las palabras y categorías que hayas añadido (no se guarda hasta que pulses «Guardar reglas»).",
                "Restaurar")) {
            cargar(Reglas.porDefecto());
            marcarCambios(true);
        }
    }

    /** @return true si se ha guardado bien */
    boolean guardar() {
        Set<String> nombres = new HashSet<>();
        for (Categoria c : trabajo.getCategorias()) {
            if (c.getNombre().isBlank()) {
                Dialogos.aviso(this, "Hay una categoría sin nombre. Ponle uno antes de guardar.");
                lista.setSelectedValue(c, true);
                return false;
            }
            if (!nombres.add(Texto.normalizarClave(c.getNombre()))) {
                Dialogos.aviso(this, "Hay dos categorías que se llaman «" + c.getNombre() + "». Cambia el nombre de una.");
                lista.setSelectedValue(c, true);
                return false;
            }
        }
        try {
            trabajo.guardar(ventana.config().archivoReglas());
        } catch (IOException e) {
            Dialogos.error(this, "No he podido guardar las reglas.", e);
            return false;
        }
        marcarCambios(false);
        ventana.reglasGuardadas(trabajo.copia());
        return true;
    }

    boolean hayCambios() {
        return cambios;
    }

    private void marcarCambios(boolean valor) {
        cambios = valor;
        btnGuardar.setEnabled(valor);
        btnDescartar.setEnabled(valor);
        ventana.marcarReglasSinGuardar(valor);
    }

    private void probar() {
        String nombre = campoPrueba.getText().trim();
        if (nombre.isEmpty()) {
            lblPrueba.setIcon(null);
            lblPrueba.setForeground(Estilo.gris());
            lblPrueba.setText("Aquí verás a dónde iría, con las reglas tal como están ahora (aunque no las hayas guardado).");
            return;
        }
        Elemento e = Elemento.soloNombre(nombre);
        Clasificacion c = new Clasificador(trabajo).clasificar(e);
        String grupo = c.categoria().getTipo() == Tipo.ASIGNATURA && !e.esCarpeta()
                ? DetectorGrupos.detectar(e.normalizado()).map(x -> " › " + x.etiqueta()).orElse("")
                : "";
        String destino = c.categoria().getTipo() == Tipo.DEJAR ? "Se queda donde está" : c.categoria().getNombre() + grupo;
        lblPrueba.setIcon(Iconos.punto(Estilo.colorDe(c.categoria()), 12));
        lblPrueba.setIconTextGap(UIScale.scale(8));
        lblPrueba.setForeground(UIManagerColor.texto());
        lblPrueba.setText("<html><b>" + Estilo.html(destino) + "</b>&nbsp;&nbsp;<font color='" + Estilo.hex(Estilo.gris()) + "'>"
                + Estilo.html(String.join(" · ", c.motivos())) + "</font></html>");
    }

    private static void alCambiarTexto(JTextComponent campo, Runnable accion) {
        campo.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                accion.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                accion.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                accion.run();
            }
        });
    }

    /** Lista de categorías: punto de color, nombre y tipo en gris. */
    private static final class RenderLista extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
            super.getListCellRendererComponent(l, v, i, s, f);
            if (v instanceof Categoria c) {
                setIcon(Iconos.punto(Estilo.colorDe(c)));
                setIconTextGap(UIScale.scale(10));
                String gris = Estilo.hex(s ? l.getSelectionForeground() : Estilo.gris());
                String nombre = c.getNombre().isBlank() ? "(sin nombre)" : c.getNombre();
                setText(c.getTipo() == Tipo.ASIGNATURA ? nombre
                        : "<html>" + Estilo.html(nombre) + "&nbsp;&nbsp;<font color='" + gris + "'>"
                        + c.getTipo().etiqueta().toLowerCase(Locale.ROOT) + "</font></html>");
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            }
            return this;
        }
    }

    /** Color de texto normal del tema actual. */
    private static final class UIManagerColor {
        static Color texto() {
            Color c = javax.swing.UIManager.getColor("Label.foreground");
            return c != null ? c : Color.BLACK;
        }
    }
}
