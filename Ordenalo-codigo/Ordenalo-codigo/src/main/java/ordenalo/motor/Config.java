package ordenalo.motor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Preferencias de la aplicación y dónde se guardan.
 * <p>
 * Si el programa se ejecuta desde su .jar, guarda todo en una carpeta «datos» junto a él
 * (modo portátil: si llevas la carpeta de Ordénalo en un USB o en Drive, tus reglas viajan contigo).
 * Si no se puede escribir ahí, usa la carpeta de configuración del usuario.
 */
public final class Config {

    private static final String ARCHIVO = "config.properties";

    private final Path carpetaDatos;
    private final Properties props = new Properties();

    private Config(Path carpetaDatos) {
        this.carpetaDatos = carpetaDatos;
    }

    public static Config cargar() {
        return en(resolverCarpetaDatos());
    }

    /** Configuración guardada en una carpeta concreta (útil para pruebas). */
    public static Config en(Path carpetaDatos) {
        Config c = new Config(carpetaDatos);
        Path archivo = carpetaDatos.resolve(ARCHIVO);
        if (Files.exists(archivo)) {
            try (Reader r = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
                c.props.load(r);
            } catch (IOException | IllegalArgumentException e) {
                // configuración estropeada: se empieza de cero
            }
        }
        return c;
    }

    public void guardar() {
        try {
            Files.createDirectories(carpetaDatos);
            try (Writer w = Files.newBufferedWriter(carpetaDatos.resolve(ARCHIVO), StandardCharsets.UTF_8)) {
                props.store(w, "Ordénalo");
            }
        } catch (IOException e) {
            // no es grave: la próxima vez se usarán los valores por defecto
        }
    }

    // ─── Dónde se guardan las cosas ──────────────────────────────────────

    private static Path resolverCarpetaDatos() {
        String propiedad = System.getProperty("ordenalo.datos");
        if (propiedad != null && !propiedad.isBlank()) {
            return Path.of(propiedad).toAbsolutePath();
        }
        Path jar = ubicacionJar();
        if (jar != null) {
            Path junto = jar.getParent().resolve("datos");
            if (sePuedeEscribir(junto)) {
                return junto;
            }
        }
        String so = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path inicio = CarpetasSistema.inicio();
        if (so.contains("win")) {
            String appData = System.getenv("APPDATA");
            return appData != null ? Path.of(appData, "Ordenalo") : inicio.resolve("AppData/Roaming/Ordenalo");
        }
        if (so.contains("mac")) {
            return inicio.resolve("Library/Application Support/Ordenalo");
        }
        String xdg = System.getenv("XDG_CONFIG_HOME");
        return (xdg != null && !xdg.isBlank() ? Path.of(xdg) : inicio.resolve(".config")).resolve("ordenalo");
    }

    /** El .jar desde el que se ejecuta el programa, o null si se ejecuta desde el IDE. */
    public static Path ubicacionJar() {
        try {
            Path p = Path.of(Config.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return p.getFileName() != null && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")
                    ? p.toAbsolutePath().normalize() : null;
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    private static boolean sePuedeEscribir(Path carpeta) {
        try {
            Files.createDirectories(carpeta);
            Path prueba = Files.createTempFile(carpeta, ".prueba", ".tmp");
            Files.delete(prueba);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Lo que nunca se debe mover al ordenar «origen»: los destinos, los datos del programa
     * y el propio programa (y sus lanzadores, si están sueltos en esa carpeta).
     */
    public List<Path> exclusionesPara(Path origen) {
        List<Path> lista = new ArrayList<>(List.of(getDestinoClase(), getDestinoOtros(), carpetaDatos));
        Path jar = ubicacionJar();
        if (jar != null) {
            lista.add(jar);
            Path carpetaJar = jar.getParent();
            if (carpetaJar != null && carpetaJar.equals(origen.toAbsolutePath().normalize())) {
                for (String nombre : List.of("Ordenalo.bat", "ordenalo.sh", "LEEME.txt")) {
                    lista.add(carpetaJar.resolve(nombre));
                }
            }
        }
        return lista;
    }

    public Path getCarpetaDatos() {
        return carpetaDatos;
    }

    public Path archivoReglas() {
        return carpetaDatos.resolve("reglas.ini");
    }

    public Path carpetaHistorial() {
        return carpetaDatos.resolve("historial");
    }

    // ─── Preferencias ────────────────────────────────────────────────────

    public Path getDestinoClase() {
        return ruta("destino.clase", CarpetasSistema.documentos().resolve("DAM"));
    }

    public void setDestinoClase(Path p) {
        props.setProperty("destino.clase", p.toString());
    }

    public Path getDestinoOtros() {
        return ruta("destino.otros", CarpetasSistema.documentos().resolve("Ordenado"));
    }

    public void setDestinoOtros(Path p) {
        props.setProperty("destino.otros", p.toString());
    }

    public Optional<Path> getUltimoOrigen() {
        String s = props.getProperty("origen");
        return s == null || s.isBlank() ? Optional.empty() : Optional.of(Path.of(s));
    }

    public void setUltimoOrigen(Path p) {
        props.setProperty("origen", p.toString());
    }

    public boolean isMoverOtros() {
        return Boolean.parseBoolean(props.getProperty("mover.otros", "true"));
    }

    public void setMoverOtros(boolean v) {
        props.setProperty("mover.otros", Boolean.toString(v));
    }

    public boolean isBuscarDuplicados() {
        return Boolean.parseBoolean(props.getProperty("duplicados", "true"));
    }

    public void setBuscarDuplicados(boolean v) {
        props.setProperty("duplicados", Boolean.toString(v));
    }

    public boolean isTemaOscuro() {
        return Boolean.parseBoolean(props.getProperty("tema.oscuro", "false"));
    }

    public void setTemaOscuro(boolean v) {
        props.setProperty("tema.oscuro", Boolean.toString(v));
    }

    private Path ruta(String clave, Path porDefecto) {
        String s = props.getProperty(clave);
        try {
            return s == null || s.isBlank() ? porDefecto : Path.of(s);
        } catch (RuntimeException e) {
            return porDefecto;
        }
    }
}
