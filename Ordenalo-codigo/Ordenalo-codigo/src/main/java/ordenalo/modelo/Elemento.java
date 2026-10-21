package ordenalo.modelo;

import ordenalo.motor.Texto;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Un archivo o carpeta suelto de la carpeta que se está ordenando.
 * Para las carpetas guarda un resumen de lo que hay dentro (tipos de archivo y algunos nombres).
 */
public final class Elemento {

    /** La carpeta es un repositorio Git (tiene .git). */
    public static final String MARCA_GIT = "git";
    /** La carpeta es una bóveda de Obsidian (tiene .obsidian). */
    public static final String MARCA_OBSIDIAN = "obsidian";
    /** La carpeta es el propio Ordénalo (tiene Ordenalo.jar). */
    public static final String MARCA_ORDENALO = "ordenalo";

    private final Path ruta;
    private final String nombre;
    private final boolean carpeta;
    private final long tamano;
    private final long modificado;
    private final String extension;
    private final Map<String, Integer> extensionesDentro;
    private final int archivosDentro;
    private final List<String> nombresDentro;
    private final boolean contenidoIncompleto;
    private final Set<String> marcas;
    private final String normalizado;
    private final String compacto;

    public Elemento(Path ruta, String nombre, boolean carpeta, long tamano, long modificado,
                    Map<String, Integer> extensionesDentro, int archivosDentro,
                    List<String> nombresDentro, boolean contenidoIncompleto) {
        this(ruta, nombre, carpeta, tamano, modificado, extensionesDentro, archivosDentro, nombresDentro,
                contenidoIncompleto, Set.of());
    }

    public Elemento(Path ruta, String nombre, boolean carpeta, long tamano, long modificado,
                    Map<String, Integer> extensionesDentro, int archivosDentro,
                    List<String> nombresDentro, boolean contenidoIncompleto, Set<String> marcas) {
        this.marcas = marcas == null ? Set.of() : Set.copyOf(marcas);
        this.ruta = ruta;
        this.nombre = nombre;
        this.carpeta = carpeta;
        this.tamano = tamano;
        this.modificado = modificado;
        this.extension = carpeta ? "" : Texto.extension(nombre);
        this.extensionesDentro = extensionesDentro == null ? Map.of() : Map.copyOf(extensionesDentro);
        this.archivosDentro = archivosDentro;
        this.nombresDentro = nombresDentro == null ? List.of() : List.copyOf(nombresDentro);
        this.contenidoIncompleto = contenidoIncompleto;
        this.normalizado = Texto.normalizarNombre(carpeta ? nombre : Texto.raiz(nombre));
        this.compacto = normalizado.replace(" ", "");
    }

    public static Elemento archivo(Path ruta, long tamano, long modificado) {
        return new Elemento(ruta, ruta.getFileName().toString(), false, tamano, modificado, null, 0, null, false);
    }

    /** Un elemento que solo existe como nombre, para probar las reglas. Sin extensión = carpeta. */
    public static Elemento soloNombre(String nombre) {
        boolean pareceCarpeta = Texto.extension(nombre).isEmpty();
        return new Elemento(null, nombre, pareceCarpeta, 0, 0, null, 0, null, false);
    }

    public Path getRuta() {
        return ruta;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean esCarpeta() {
        return carpeta;
    }

    /** Bytes del archivo, o la suma de lo que hay dentro si es una carpeta. */
    public long getTamano() {
        return tamano;
    }

    /** Fecha de modificación en milisegundos. */
    public long getModificado() {
        return modificado;
    }

    /** Extensión en minúsculas sin punto ("" en carpetas). */
    public String getExtension() {
        return extension;
    }

    public Map<String, Integer> getExtensionesDentro() {
        return extensionesDentro;
    }

    public int getArchivosDentro() {
        return archivosDentro;
    }

    /** Nombres (ya normalizados) de archivos y subcarpetas de los primeros niveles. */
    public List<String> getNombresDentro() {
        return nombresDentro;
    }

    /** True si la carpeta era tan grande que no se ha mirado entera. */
    public boolean isContenidoIncompleto() {
        return contenidoIncompleto;
    }

    /** Pistas sobre qué es la carpeta: {@link #MARCA_GIT}, {@link #MARCA_OBSIDIAN}… */
    public boolean tieneMarca(String marca) {
        return marcas.contains(marca);
    }

    /** El nombre en palabras sueltas: « tarea 3 si ». */
    public String normalizado() {
        return normalizado;
    }

    /** El nombre sin espacios: «tarea3si». */
    public String compacto() {
        return compacto;
    }

    @Override
    public String toString() {
        return nombre;
    }
}
