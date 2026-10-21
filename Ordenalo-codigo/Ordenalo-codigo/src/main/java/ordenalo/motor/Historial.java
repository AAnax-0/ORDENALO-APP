package ordenalo.motor;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Registro de cada ordenación: qué se movió, de dónde y a dónde. Es lo que permite deshacer.
 * <p>
 * Cada ordenación es un archivo de texto en la carpeta del historial. Se escribe línea a
 * línea mientras se mueven las cosas, así que aunque se cierre el programa a medias se puede deshacer.
 */
public final class Historial {

    private static final DateTimeFormatter ID = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path carpeta;

    public Historial(Path carpeta) {
        this.carpeta = carpeta;
    }

    public Path getCarpeta() {
        return carpeta;
    }

    /** Empieza el registro de una ordenación nueva. */
    public Diario nuevo(Path origen) throws IOException {
        Files.createDirectories(carpeta);
        LocalDateTime ahora = LocalDateTime.now().withNano(0);
        String id = ID.format(ahora);
        Path archivo = carpeta.resolve(id + ".txt");
        for (int n = 2; Files.exists(archivo); n++) {
            archivo = carpeta.resolve(id + "-" + n + ".txt");
        }
        BufferedWriter w = Files.newBufferedWriter(archivo, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        Diario diario = new Diario(archivo, w);
        diario.linea("# Ordénalo: registro de una ordenación. Si lo borras, ya no se podrá deshacer.");
        diario.linea("# fecha=" + ahora);
        diario.linea("# equipo=" + nombreEquipo());
        diario.linea("# origen=" + origen.toAbsolutePath().normalize());
        return diario;
    }

    /** Las ordenaciones guardadas, de la más reciente a la más antigua. */
    public List<Ejecucion> listar() {
        if (!Files.isDirectory(carpeta)) {
            return List.of();
        }
        List<Ejecucion> lista = new ArrayList<>();
        try (Stream<Path> s = Files.list(carpeta)) {
            for (Path p : s.filter(x -> x.getFileName().toString().endsWith(".txt")).toList()) {
                try {
                    lista.add(Ejecucion.leer(p));
                } catch (IOException | RuntimeException e) {
                    // archivo que no es nuestro o está estropeado: se ignora
                }
            }
        } catch (IOException e) {
            return List.of();
        }
        lista.sort(Comparator.comparing(Ejecucion::getFecha).reversed().thenComparing(Ejecucion::getId, Comparator.reverseOrder()));
        return lista;
    }

    /** La última ordenación de este ordenador que se puede deshacer. */
    public Optional<Ejecucion> ultimaDeshacible() {
        return listar().stream().filter(e -> e.sePuedeDeshacer() && e.esDeEsteEquipo()).findFirst();
    }

    /** Apunta que algo de esta ordenación ya se ha devuelto a su sitio (así, al deshacer, no se vuelve a intentar). */
    public void marcarReparado(Ejecucion e, Path original) throws IOException {
        Files.writeString(e.getArchivo(), "# reparado=" + original.toAbsolutePath().normalize() + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    public void marcarDeshecha(Ejecucion e, int restaurados, int errores) throws IOException {
        Files.writeString(e.getArchivo(), "# deshecho=" + LocalDateTime.now().withNano(0)
                        + " restaurados=" + restaurados + " errores=" + errores + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    public static String nombreEquipo() {
        String nombre = System.getenv("COMPUTERNAME");
        if (nombre == null || nombre.isBlank()) {
            nombre = System.getenv("HOSTNAME");
        }
        if (nombre == null || nombre.isBlank()) {
            try {
                nombre = Files.readString(Path.of("/etc/hostname")).trim();
            } catch (IOException | RuntimeException e) {
                nombre = null;
            }
        }
        if (nombre == null || nombre.isBlank()) {
            try {
                nombre = InetAddress.getLocalHost().getHostName();
            } catch (IOException e) {
                nombre = "este-equipo";
            }
        }
        return nombre;
    }

    // ─── Escritura ───────────────────────────────────────────────────────

    /** Escribe el registro de una ordenación mientras se hace. */
    public static final class Diario implements Closeable {
        private final Path archivo;
        private final BufferedWriter w;

        private Diario(Path archivo, BufferedWriter w) {
            this.archivo = archivo;
            this.w = w;
        }

        public Path getArchivo() {
            return archivo;
        }

        public void carpetaCreada(Path p) throws IOException {
            linea(Operacion.Tipo.CREADA + "\t" + p);
        }

        public void movido(Path de, Path a) throws IOException {
            linea(Operacion.Tipo.MOVIDO + "\t" + de + "\t" + a);
        }

        public void carpetaBorrada(Path p) throws IOException {
            linea(Operacion.Tipo.BORRADA + "\t" + p);
        }

        public void terminar(int elementos, int errores) throws IOException {
            linea("# fin=" + LocalDateTime.now().withNano(0) + " elementos=" + elementos + " errores=" + errores);
        }

        private void linea(String texto) throws IOException {
            w.write(texto);
            w.newLine();
            w.flush(); // cada línea al disco: si algo falla a medias, se puede deshacer lo hecho
        }

        @Override
        public void close() throws IOException {
            w.close();
        }
    }

    // ─── Lectura ─────────────────────────────────────────────────────────

    /** Un paso de una ordenación. En MOVIDO, «a» es de dónde salió y «b» a dónde fue. */
    public record Operacion(Tipo tipo, Path a, Path b) {
        public enum Tipo { CREADA, MOVIDO, BORRADA }
    }

    /** Una ordenación ya hecha, leída de su archivo. */
    public static final class Ejecucion {
        private final Path archivo;
        private final String id;
        private LocalDateTime fecha;
        private String equipo = "";
        private Path origen;
        private int elementos = -1;
        private int errores;
        private boolean terminada;
        private LocalDateTime deshecha;
        private final List<Operacion> operaciones = new ArrayList<>();
        private final java.util.Set<Path> reparados = new java.util.HashSet<>();

        private Ejecucion(Path archivo) {
            this.archivo = archivo;
            String nombre = archivo.getFileName().toString();
            this.id = nombre.substring(0, nombre.length() - 4);
        }

        static Ejecucion leer(Path archivo) throws IOException {
            Ejecucion e = new Ejecucion(archivo);
            for (String linea : Files.readString(archivo, StandardCharsets.UTF_8).split("\\R")) {
                if (linea.isBlank()) {
                    continue;
                }
                if (linea.startsWith("# ")) {
                    e.leerDato(linea.substring(2));
                    continue;
                }
                String[] partes = linea.split("\t");
                Operacion.Tipo tipo = Operacion.Tipo.valueOf(partes[0]);
                Path a = Path.of(partes[1]);
                Path b = partes.length > 2 ? Path.of(partes[2]) : null;
                e.operaciones.add(new Operacion(tipo, a, b));
            }
            if (e.fecha == null) {
                throw new IOException("No es un registro de Ordénalo");
            }
            return e;
        }

        private void leerDato(String dato) {
            if (dato.startsWith("reparado=")) {
                reparados.add(Path.of(dato.substring(9)));
            } else if (dato.startsWith("origen=")) {
                origen = Path.of(dato.substring(7));
            } else if (dato.startsWith("equipo=")) {
                equipo = dato.substring(7);
            } else if (dato.startsWith("fecha=")) {
                fecha = fecha(dato.substring(6));
            } else if (dato.startsWith("fin=")) {
                terminada = true;
                for (String parte : dato.split(" ")) {
                    if (parte.startsWith("elementos=")) {
                        elementos = Integer.parseInt(parte.substring(10));
                    } else if (parte.startsWith("errores=")) {
                        errores = Integer.parseInt(parte.substring(8));
                    }
                }
            } else if (dato.startsWith("deshecho=")) {
                deshecha = fecha(dato.substring(9).split(" ")[0]);
            }
        }

        private static LocalDateTime fecha(String texto) {
            try {
                return LocalDateTime.parse(texto.trim());
            } catch (DateTimeParseException e) {
                return LocalDateTime.MIN;
            }
        }

        public Path getArchivo() {
            return archivo;
        }

        public String getId() {
            return id;
        }

        public LocalDateTime getFecha() {
            return fecha;
        }

        public String getEquipo() {
            return equipo;
        }

        public Path getOrigen() {
            return origen;
        }

        /** Cuántos elementos se ordenaron (las carpetas cuentan como uno). */
        public int getElementos() {
            return elementos >= 0 ? elementos : movimientos();
        }

        public int getErrores() {
            return errores;
        }

        /** Cuántas cosas se movieron de verdad (contando archivo a archivo al juntar carpetas). */
        public int movimientos() {
            return (int) operaciones.stream().filter(o -> o.tipo() == Operacion.Tipo.MOVIDO).count();
        }

        public boolean isTerminada() {
            return terminada;
        }

        public LocalDateTime getDeshecha() {
            return deshecha;
        }

        public boolean sePuedeDeshacer() {
            return deshecha == null && movimientos() > 0;
        }

        public boolean esDeEsteEquipo() {
            return equipo.equalsIgnoreCase(nombreEquipo());
        }

        /** Los pasos de la ordenación, sin los que ya se han devuelto a su sitio con el reparador. */
        public List<Operacion> getOperaciones() {
            return operaciones.stream()
                    .filter(o -> !(o.tipo() == Operacion.Tipo.MOVIDO && reparados.contains(o.a())))
                    .toList();
        }
    }
}
