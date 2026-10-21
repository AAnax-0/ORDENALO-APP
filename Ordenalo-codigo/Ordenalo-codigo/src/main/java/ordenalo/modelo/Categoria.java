package ordenalo.modelo;

import ordenalo.motor.ConjuntoClaves;
import ordenalo.motor.Texto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Un sitio al que pueden ir las cosas: una asignatura, algo personal o un tipo de archivo.
 * Sus palabras clave y extensiones deciden qué va aquí.
 */
public final class Categoria {

    public enum Tipo {
        /** Va a la carpeta de clase y se agrupa por tareas (Tarea 3, Actividad 2…). */
        ASIGNATURA("asignatura", "Asignatura"),
        /** Algo tuyo que no es de clase (proyectos personales…). Va a la carpeta de lo demás. */
        PERSONAL("personal", "Personal"),
        /** Se reconoce por la extensión: imágenes, PDF, instaladores… Va a la carpeta de lo demás. */
        ARCHIVO("archivo", "Tipo de archivo"),
        DUPLICADO("duplicado", "Copias repetidas"),
        DEJAR("dejar", "No mover");

        private final String clave;
        private final String etiqueta;

        Tipo(String clave, String etiqueta) {
            this.clave = clave;
            this.etiqueta = etiqueta;
        }

        public String clave() {
            return clave;
        }

        public String etiqueta() {
            return etiqueta;
        }

        /** Asignaturas y temas personales se reconocen por el nombre y por lo que hay dentro. */
        public boolean esTematica() {
            return this == ASIGNATURA || this == PERSONAL;
        }

        public static Tipo desdeTexto(String texto) {
            String t = Texto.normalizarClave(texto == null ? "" : texto);
            if (t.startsWith("personal")) {
                return PERSONAL;
            }
            if (t.startsWith("archivo") || t.startsWith("tipo")) {
                return ARCHIVO;
            }
            return ASIGNATURA;
        }
    }

    private String nombre;
    private Tipo tipo;
    private String carpeta;
    private final List<String> palabras = new ArrayList<>();
    private final Set<String> extensiones = new LinkedHashSet<>();
    private String color = "#94a3b8";
    private boolean soloPorTipo;
    private final boolean integrada;
    private ConjuntoClaves claves = ConjuntoClaves.VACIO;

    public Categoria(String nombre, Tipo tipo) {
        this(nombre, tipo, false);
    }

    private Categoria(String nombre, Tipo tipo, boolean integrada) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.carpeta = nombre;
        this.integrada = integrada;
    }

    /** Categorías fijas de la aplicación (no se guardan en el archivo de reglas). */
    public static Categoria integrada(String nombre, Tipo tipo, String carpeta, String color) {
        Categoria c = new Categoria(nombre, tipo, true);
        c.carpeta = carpeta;
        c.color = color;
        return c;
    }

    public Categoria copia() {
        Categoria c = new Categoria(nombre, tipo, integrada);
        c.carpeta = carpeta;
        c.color = color;
        c.soloPorTipo = soloPorTipo;
        c.setPalabras(palabras);
        c.setExtensiones(extensiones);
        return c;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre == null ? "" : nombre.trim();
    }

    public Tipo getTipo() {
        return tipo;
    }

    public void setTipo(Tipo tipo) {
        this.tipo = tipo;
    }

    /** Nombre de la carpeta donde acaba (si no se ha puesto, el de la categoría). */
    public String getCarpeta() {
        return carpeta == null || carpeta.isBlank() ? nombre : carpeta;
    }

    public void setCarpeta(String carpeta) {
        this.carpeta = carpeta == null ? "" : carpeta.trim();
    }

    public List<String> getPalabras() {
        return List.copyOf(palabras);
    }

    public void setPalabras(Collection<String> nuevas) {
        List<String> copia = new ArrayList<>(nuevas);
        palabras.clear();
        for (String p : copia) {
            if (p != null && !p.isBlank()) {
                palabras.add(p.trim());
            }
        }
        claves = new ConjuntoClaves(palabras);
    }

    public Set<String> getExtensiones() {
        return Collections.unmodifiableSet(extensiones);
    }

    public void setExtensiones(Collection<String> nuevas) {
        List<String> copia = new ArrayList<>(nuevas);
        extensiones.clear();
        for (String e : copia) {
            if (e == null) {
                continue;
            }
            String limpia = e.trim().toLowerCase(Locale.ROOT);
            while (limpia.startsWith(".") || limpia.startsWith("*")) {
                limpia = limpia.substring(1);
            }
            if (!limpia.isEmpty()) {
                extensiones.add(limpia);
            }
        }
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    /** Si es true, la extensión manda aunque el nombre parezca de una asignatura (instaladores, ISOs…). */
    public boolean isSoloPorTipo() {
        return soloPorTipo;
    }

    public void setSoloPorTipo(boolean soloPorTipo) {
        this.soloPorTipo = soloPorTipo;
    }

    public boolean isIntegrada() {
        return integrada;
    }

    public ConjuntoClaves getClaves() {
        return claves;
    }

    @Override
    public String toString() {
        return nombre;
    }
}
