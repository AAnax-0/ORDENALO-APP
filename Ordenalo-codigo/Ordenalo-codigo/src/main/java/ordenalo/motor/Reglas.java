package ordenalo.motor;

import ordenalo.modelo.Categoria;
import ordenalo.modelo.Categoria.Tipo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * El conjunto de categorías (asignaturas, temas personales y tipos de archivo).
 * Se guardan en un archivo de texto tipo INI que se puede editar a mano.
 */
public final class Reglas {

    private static final String RECURSO = "/ordenalo/reglas-por-defecto.ini";

    private final List<Categoria> categorias = new ArrayList<>();

    private final Categoria dejar = Categoria.integrada("Dejar donde está", Tipo.DEJAR, "", "#9ca3af");
    private final Categoria duplicados = Categoria.integrada("Duplicados", Tipo.DUPLICADO, "Duplicados", "#94a3b8");
    private final Categoria sinAsignatura = Categoria.integrada("Sin asignatura", Tipo.ASIGNATURA, "Sin asignatura", "#cbd5e1");
    private final Categoria otrosArchivos = Categoria.integrada("Otros archivos", Tipo.ARCHIVO, "Otros", "#a8a29e");

    // ─── Cargar y guardar ────────────────────────────────────────────────

    public static String textoPorDefecto() {
        try (InputStream in = Reglas.class.getResourceAsStream(RECURSO)) {
            if (in == null) {
                throw new IllegalStateException("Falta " + RECURSO + " dentro del programa");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Reglas porDefecto() {
        return desdeTexto(textoPorDefecto());
    }

    /** Lee las reglas del archivo; si no existe, lo crea con las de fábrica. */
    public static Reglas cargar(Path archivo) throws IOException {
        if (!Files.exists(archivo)) {
            Reglas reglas = porDefecto();
            reglas.guardar(archivo);
            return reglas;
        }
        Reglas reglas = desdeTexto(Files.readString(archivo, StandardCharsets.UTF_8));
        if (reglas.categorias.isEmpty()) {
            throw new IOException("El archivo de reglas está vacío o no se entiende");
        }
        return reglas;
    }

    public static Reglas desdeTexto(String texto) {
        Reglas reglas = new Reglas();
        Categoria actual = null;
        for (String linea : texto.split("\\R")) {
            String l = linea.strip();
            if (l.isEmpty() || l.startsWith("#") || l.startsWith(";")) {
                continue;
            }
            if (l.startsWith("[") && l.endsWith("]")) {
                String nombre = l.substring(1, l.length() - 1).strip();
                actual = nombre.isEmpty() ? null : new Categoria(nombre, Tipo.ASIGNATURA);
                if (actual != null) {
                    reglas.categorias.add(actual);
                }
                continue;
            }
            int igual = l.indexOf('=');
            if (actual == null || igual < 0) {
                continue;
            }
            String clave = Texto.normalizarClave(l.substring(0, igual));
            String valor = l.substring(igual + 1).strip();
            switch (clave) {
                case "tipo" -> actual.setTipo(Tipo.desdeTexto(valor));
                case "carpeta" -> actual.setCarpeta(valor);
                case "palabras", "palabras clave" -> actual.setPalabras(Texto.separarLista(valor));
                case "extensiones" -> actual.setExtensiones(Texto.separarLista(valor.replace(' ', ',')));
                case "color" -> actual.setColor(valor);
                case "solo por tipo" -> actual.setSoloPorTipo(esSi(valor));
                default -> { /* clave desconocida: se ignora */ }
            }
        }
        return reglas;
    }

    private static boolean esSi(String valor) {
        String v = Texto.normalizarClave(valor);
        return v.equals("si") || v.equals("true") || v.equals("1") || v.equals("yes");
    }

    public void guardar(Path archivo) throws IOException {
        Path carpeta = archivo.toAbsolutePath().getParent();
        if (carpeta != null) {
            Files.createDirectories(carpeta);
        }
        Files.writeString(archivo, aTexto(), StandardCharsets.UTF_8);
    }

    public String aTexto() {
        StringBuilder sb = new StringBuilder();
        // Se conserva el comentario explicativo de las reglas de fábrica.
        for (String linea : textoPorDefecto().split("\\R")) {
            if (!linea.startsWith("#")) {
                break;
            }
            sb.append(linea).append('\n');
        }
        for (Categoria c : categorias) {
            sb.append('\n').append('[').append(c.getNombre()).append("]\n");
            sb.append("tipo = ").append(c.getTipo().clave()).append('\n');
            sb.append("carpeta = ").append(c.getCarpeta()).append('\n');
            if (!c.getPalabras().isEmpty()) {
                sb.append("palabras = ").append(String.join(", ", c.getPalabras())).append('\n');
            }
            if (!c.getExtensiones().isEmpty()) {
                sb.append("extensiones = ").append(String.join(", ", c.getExtensiones())).append('\n');
            }
            if (c.isSoloPorTipo()) {
                sb.append("solo por tipo = si\n");
            }
            sb.append("color = ").append(c.getColor()).append('\n');
        }
        return sb.toString();
    }

    public Reglas copia() {
        Reglas r = new Reglas();
        for (Categoria c : categorias) {
            r.categorias.add(c.copia());
        }
        return r;
    }

    // ─── Consultas ───────────────────────────────────────────────────────

    /** Las categorías del archivo, en orden (la lista se puede modificar desde el editor). */
    public List<Categoria> getCategorias() {
        return categorias;
    }

    /** Asignaturas y temas personales: compiten por los nombres. */
    public List<Categoria> tematicas() {
        return categorias.stream().filter(c -> c.getTipo().esTematica()).toList();
    }

    public List<Categoria> asignaturas() {
        return categorias.stream().filter(c -> c.getTipo() == Tipo.ASIGNATURA).toList();
    }

    public List<Categoria> tiposDeArchivo() {
        return categorias.stream().filter(c -> c.getTipo() == Tipo.ARCHIVO).toList();
    }

    /** Todo a lo que el usuario puede mandar un elemento, en un orden razonable. */
    public List<Categoria> paraElegir() {
        List<Categoria> lista = new ArrayList<>(asignaturas());
        lista.add(sinAsignatura);
        categorias.stream().filter(c -> c.getTipo() == Tipo.PERSONAL).forEach(lista::add);
        lista.addAll(tiposDeArchivo());
        lista.add(otrosArchivos);
        lista.add(duplicados);
        lista.add(dejar);
        return lista;
    }

    public Categoria dejar() {
        return dejar;
    }

    public Categoria duplicados() {
        return duplicados;
    }

    public Categoria sinAsignatura() {
        return sinAsignatura;
    }

    public Categoria otrosArchivos() {
        return otrosArchivos;
    }
}
