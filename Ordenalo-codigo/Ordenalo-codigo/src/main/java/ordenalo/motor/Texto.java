package ordenalo.motor;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilidades para comparar nombres sin que importen las mayúsculas, las tildes o los separadores.
 * <p>
 * La idea clave: convertir cualquier nombre en palabras sueltas separadas por un espacio.
 * {@code "Tarea3_SI-final"} y {@code "tarea 3 SI final"} acaban siendo lo mismo: {@code " tarea 3 si final "}.
 */
public final class Texto {

    private Texto() {
    }

    /**
     * Palabras que mezclan mayúsculas y no hay que partir. Si no, «JavaScript» se partiría en
     * «Java Script» y parecería de Programación, o «SQLite» en «SQ Lite».
     */
    private static final List<Pattern> PROTEGIDAS = new ArrayList<>();

    static {
        String[] palabras = {"javascript", "typescript", "postgresql", "phpmyadmin", "clonezilla", "filezilla",
                "virtualbox", "powershell", "powerpoint", "intellij", "netbeans", "mongodb", "mariadb", "onedrive",
                "youtube", "github", "gitlab", "sqlite", "mysql", "vmware", "junit", "macos", "iphone", "ipad"};
        Arrays.sort(palabras, Comparator.comparingInt(String::length).reversed());
        for (String p : palabras) {
            PROTEGIDAS.add(Pattern.compile(Pattern.quote(p), Pattern.CASE_INSENSITIVE));
        }
    }

    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
    private static final Pattern MINUSCULA_MAYUSCULA = Pattern.compile("(?<=[a-z])(?=[A-Z])");
    private static final Pattern SIGLAS_Y_PALABRA = Pattern.compile("(?<=[A-Z])(?=[A-Z][a-z]{2,})");
    private static final Pattern LETRA_NUMERO = Pattern.compile("(?<=[A-Za-z])(?=[0-9])|(?<=[0-9])(?=[A-Za-z])");
    private static final Pattern NO_ALFANUMERICO = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EXTENSION_VALIDA = Pattern.compile("[a-z0-9]*[a-z][a-z0-9]*");
    private static final Pattern PROHIBIDOS_WINDOWS = Pattern.compile("[<>:\"/\\\\|?*\\x00-\\x1F]");

    /** Quita tildes y diéresis: «Programación» → «Programacion». */
    public static String sinTildes(String s) {
        return MARCAS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
    }

    /**
     * Convierte el nombre de un archivo o carpeta en palabras sueltas, en minúsculas, sin tildes
     * y con un espacio al principio y al final (así es fácil buscar palabras completas).
     * Separa también las mayúsculas pegadas y los números: «ActividadSI2» → « actividad si 2 ».
     */
    public static String normalizarNombre(String nombre) {
        String s = sinTildes(nombre);
        for (Pattern p : PROTEGIDAS) {
            Matcher m = p.matcher(s);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String encontrado = m.group();
                String minusculas = encontrado.toLowerCase(Locale.ROOT);
                // Solo hace falta protegerla si lleva mayúsculas (si no, no se va a partir).
                String reemplazo = encontrado.equals(minusculas) ? encontrado : " " + minusculas + " ";
                m.appendReplacement(sb, Matcher.quoteReplacement(reemplazo));
            }
            m.appendTail(sb);
            s = sb.toString();
        }
        s = MINUSCULA_MAYUSCULA.matcher(s).replaceAll(" ");
        s = SIGLAS_Y_PALABRA.matcher(s).replaceAll(" ");
        s = LETRA_NUMERO.matcher(s).replaceAll(" ");
        s = NO_ALFANUMERICO.matcher(s.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
        return " " + s + " ";
    }

    /** Normaliza una palabra clave escrita por el usuario (sin partir mayúsculas). */
    public static String normalizarClave(String clave) {
        String s = sinTildes(clave).toLowerCase(Locale.ROOT);
        s = LETRA_NUMERO.matcher(s).replaceAll(" ");
        return NO_ALFANUMERICO.matcher(s).replaceAll(" ").trim();
    }

    /** ¿Son el mismo nombre si no miramos mayúsculas, tildes ni separadores? */
    public static boolean mismoNombre(String a, String b) {
        return normalizarClave(a).equals(normalizarClave(b));
    }

    /** Extensión en minúsculas y sin punto («Foto.JPG» → «jpg»), o "" si no tiene. */
    public static String extension(String nombre) {
        int i = nombre.lastIndexOf('.');
        if (i <= 0 || i == nombre.length() - 1) {
            return "";
        }
        String ext = nombre.substring(i + 1).toLowerCase(Locale.ROOT);
        if (ext.length() > 12 || !EXTENSION_VALIDA.matcher(ext).matches()) {
            return "";
        }
        return ext;
    }

    /** Nombre sin la extensión: «Tarea 3.docx» → «Tarea 3». */
    public static String raiz(String nombre) {
        String ext = extension(nombre);
        return ext.isEmpty() ? nombre : nombre.substring(0, nombre.length() - ext.length() - 1);
    }

    /** Separa una lista escrita a mano: «a, b; c» → [a, b, c]. */
    public static List<String> separarLista(String texto) {
        List<String> lista = new ArrayList<>();
        if (texto == null) {
            return lista;
        }
        for (String parte : texto.split("[,;\\n\\r]")) {
            String limpio = parte.trim();
            if (!limpio.isEmpty()) {
                lista.add(limpio);
            }
        }
        return lista;
    }

    /** «a», «b» y «c» */
    public static String citar(Collection<String> palabras) {
        List<String> lista = new ArrayList<>();
        for (String p : palabras) {
            lista.add("«" + p + "»");
        }
        if (lista.size() <= 1) {
            return String.join("", lista);
        }
        return String.join(", ", lista.subList(0, lista.size() - 1)) + " y " + lista.get(lista.size() - 1);
    }

    /** 1536 → «1,5 KB» */
    public static String tamano(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] unidades = {"KB", "MB", "GB", "TB"};
        double valor = bytes;
        int i = -1;
        while (valor >= 1024 && i < unidades.length - 1) {
            valor /= 1024;
            i++;
        }
        return String.format(Locale.forLanguageTag("es-ES"), valor >= 100 ? "%.0f %s" : "%.1f %s", valor, unidades[i]);
    }

    /** Deja un nombre de carpeta válido en Windows, Linux y Mac. */
    public static String limpiarNombreCarpeta(String nombre) {
        String s = PROHIBIDOS_WINDOWS.matcher(nombre == null ? "" : nombre).replaceAll("-").trim();
        while (s.endsWith(".") || s.endsWith(" ")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty()) {
            return "Carpeta";
        }
        if (s.toUpperCase(Locale.ROOT).matches("CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9]")) {
            s = s + "_";
        }
        return s;
    }

    /** Como {@link #limpiarNombreCarpeta} pero admite subcarpetas: «Tema 2/Ejercicios». */
    public static String limpiarRuta(String ruta) {
        List<String> partes = new ArrayList<>();
        for (String parte : (ruta == null ? "" : ruta).split("[/\\\\]+")) {
            if (!parte.isBlank() && !parte.trim().equals(".") && !parte.trim().equals("..")) {
                partes.add(limpiarNombreCarpeta(parte));
            }
        }
        return String.join("/", partes);
    }
}
