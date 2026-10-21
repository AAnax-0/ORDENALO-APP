package ordenalo.motor;

import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Encuentra el Escritorio, Descargas y Documentos en Windows, Linux y Mac
 * (aunque estén en OneDrive o se llamen en español), y avisa de carpetas peligrosas.
 */
public final class CarpetasSistema {

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private CarpetasSistema() {
    }

    public static Path inicio() {
        return Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
    }

    public static Path escritorio() {
        if (WINDOWS) {
            // En Windows la «carpeta de inicio» de FileSystemView es el Escritorio (también si está en OneDrive).
            Path shell = desdeShell(true);
            if (shell != null) {
                return shell;
            }
        }
        return primeraQueExista(xdg().get("DESKTOP"), "Desktop", "Escritorio", "OneDrive/Desktop", "OneDrive/Escritorio");
    }

    public static Path documentos() {
        if (WINDOWS) {
            Path shell = desdeShell(false);
            if (shell != null) {
                return shell;
            }
        }
        return primeraQueExista(xdg().get("DOCUMENTS"), "Documents", "Documentos", "OneDrive/Documents", "OneDrive/Documentos");
    }

    public static Path descargas() {
        return primeraQueExista(xdg().get("DOWNLOAD"), "Downloads", "Descargas");
    }

    private static Path desdeShell(boolean escritorio) {
        try {
            FileSystemView fsv = FileSystemView.getFileSystemView();
            File f = escritorio ? fsv.getHomeDirectory() : fsv.getDefaultDirectory();
            if (f != null && f.isDirectory()) {
                Path p = f.toPath().toAbsolutePath().normalize();
                if (!p.equals(inicio())) {
                    return p;
                }
            }
        } catch (RuntimeException | Error e) {
            // sin interfaz gráfica o sin acceso al shell: seguimos con las rutas típicas
        }
        return null;
    }

    private static Path primeraQueExista(String rutaXdg, String... candidatas) {
        if (rutaXdg != null) {
            Path p = Path.of(rutaXdg);
            if (Files.isDirectory(p) && !p.toAbsolutePath().normalize().equals(inicio())) {
                return p.toAbsolutePath().normalize();
            }
        }
        for (String c : candidatas) {
            Path p = inicio().resolve(c);
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        return inicio().resolve(candidatas[0]);
    }

    /** Lee ~/.config/user-dirs.dirs (Linux), donde se dice cómo se llama el Escritorio en tu idioma. */
    private static Map<String, String> xdg() {
        Map<String, String> mapa = new HashMap<>();
        if (WINDOWS) {
            return mapa;
        }
        Path archivo = inicio().resolve(".config/user-dirs.dirs");
        try {
            for (String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
                String l = linea.trim();
                if (!l.startsWith("XDG_") || !l.contains("=")) {
                    continue;
                }
                String clave = l.substring(4, l.indexOf('=')).replace("_DIR", "");
                String valor = l.substring(l.indexOf('=') + 1).replace("\"", "").replace("$HOME", inicio().toString());
                mapa.put(clave, valor);
            }
        } catch (IOException | RuntimeException e) {
            // no existe: se usan los nombres típicos
        }
        return mapa;
    }

    /**
     * Carpetas que no tiene sentido ordenar porque moverían cosas del sistema:
     * la raíz de un disco, tu carpeta de usuario entera, Windows, Program Files…
     */
    public static boolean esPeligrosa(Path carpeta) {
        Path p = carpeta.toAbsolutePath().normalize();
        if (p.getParent() == null) {
            return true;
        }
        Path inicio = inicio();
        if (inicio.startsWith(p)) {
            return true; // tu carpeta de usuario o algo que la contiene
        }
        if (p.startsWith(inicio.resolve("AppData")) || p.startsWith(inicio.resolve("Library"))) {
            return true;
        }
        if (p.startsWith(inicio)) {
            for (Path parte : inicio.relativize(p)) {
                if (parte.toString().startsWith(".")) {
                    return true; // carpetas ocultas de configuración (.config, .m2…)
                }
            }
        }
        String s = p.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        List<String> sistema = List.of("c:/windows", "c:/program files", "c:/program files (x86)", "c:/programdata",
                "/bin", "/sbin", "/usr", "/etc", "/lib", "/lib64", "/var", "/sys", "/proc", "/boot", "/dev",
                "/system", "/library", "/applications", "/private");
        for (String prefijo : sistema) {
            if (s.equals(prefijo) || s.startsWith(prefijo + "/")) {
                return true;
            }
        }
        return false;
    }
}
