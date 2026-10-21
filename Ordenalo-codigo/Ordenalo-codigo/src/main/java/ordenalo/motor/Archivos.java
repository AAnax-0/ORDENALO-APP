package ordenalo.motor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Operaciones con archivos que se usan en varios sitios. */
public final class Archivos {

    private Archivos() {
    }

    /** Ruta en minúsculas para comparar (en Windows «Tarea.docx» y «tarea.docx» son el mismo archivo). */
    public static String clave(Path p) {
        return p.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }

    public static boolean existe(Path p) {
        return Files.exists(p, LinkOption.NOFOLLOW_LINKS);
    }

    /** La propia ruta si está libre, o «nombre (2).ext», «nombre (3).ext»… */
    public static Path nombreLibre(Path p) {
        return nombreLibre(p, Archivos::existe);
    }

    public static Path nombreLibre(Path p, Predicate<Path> ocupado) {
        if (!ocupado.test(p)) {
            return p;
        }
        String nombre = p.getFileName().toString();
        String ext = Texto.extension(nombre);
        String extOriginal = ext.isEmpty() ? "" : nombre.substring(nombre.length() - ext.length() - 1);
        String base = nombre.substring(0, nombre.length() - extOriginal.length()).replaceFirst("\\s\\(\\d+\\)$", "");
        for (int i = 2; i < 100_000; i++) {
            Path candidata = p.resolveSibling(base + " (" + i + ")" + extOriginal);
            if (!ocupado.test(candidata)) {
                return candidata;
            }
        }
        throw new IllegalStateException("No encuentro un nombre libre para " + p);
    }

    /** ¿Tienen exactamente el mismo contenido? */
    public static boolean mismoContenido(Path a, Path b) {
        try {
            return Files.size(a) == Files.size(b) && Files.mismatch(a, b) == -1L;
        } catch (IOException e) {
            return false;
        }
    }

    /** Huella SHA-256 del contenido, o null si no se puede leer. */
    public static String huella(Path archivo) {
        try (InputStream in = Files.newInputStream(archivo)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                md.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    public static boolean carpetaVacia(Path carpeta) throws IOException {
        try (DirectoryStream<Path> s = Files.newDirectoryStream(carpeta)) {
            return !s.iterator().hasNext();
        }
    }

    /**
     * Mueve un archivo o carpeta. Si es una carpeta y va a otra unidad (otro disco, un USB),
     * Java no puede moverla de golpe, así que se mueve su contenido uno a uno.
     */
    public static void mover(Path origen, Path destino) throws IOException {
        try {
            Files.move(origen, destino);
        } catch (DirectoryNotEmptyException e) {
            Files.createDirectories(destino);
            List<Path> hijos;
            try (Stream<Path> s = Files.list(origen)) {
                hijos = s.toList();
            }
            for (Path hijo : hijos) {
                mover(hijo, destino.resolve(hijo.getFileName().toString()));
            }
            Files.delete(origen);
        }
    }

    /** Explica un error de archivos en cristiano. */
    public static String explicar(Exception ex) {
        if (ex instanceof NoSuchFileException) {
            return "ya no está en su sitio";
        }
        if (ex instanceof FileAlreadyExistsException) {
            return "ya hay algo con ese nombre en el destino";
        }
        String mensaje = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        String m = mensaje.toLowerCase(Locale.ROOT);
        if (m.contains("used by another process") || m.contains("utilizado por otro proceso")
                || m.contains("siendo utilizado") || m.contains("en uso")) {
            return "está abierto en otro programa: ciérralo y vuelve a intentarlo";
        }
        if (ex instanceof AccessDeniedException || m.contains("access is denied") || m.contains("acceso denegado")) {
            return "no me deja moverlo (¿está abierto en otro programa o no tienes permiso?)";
        }
        return mensaje;
    }
}
