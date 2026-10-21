package ordenalo.motor;

import ordenalo.modelo.Plan;
import ordenalo.modelo.Propuesta;
import ordenalo.motor.Historial.Diario;
import ordenalo.motor.Historial.Ejecucion;
import ordenalo.motor.Historial.Operacion;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Aplica un plan (mueve las cosas) y lo deshace.
 * <p>
 * Reglas de oro: nunca se borra ni se sobrescribe nada. Si algo choca, se renombra
 * o se junta; todo queda apuntado en el historial para poder dar marcha atrás.
 */
public final class Ejecutor {

    @FunctionalInterface
    public interface Progreso {
        void avance(int hechos, int total, String que);
    }

    /** Lo que ha pasado al aplicar o deshacer. */
    public static final class Informe {
        private int correctos;
        private final List<String> errores = new ArrayList<>();
        private final List<String> avisos = new ArrayList<>();
        private Path registro;

        public int getCorrectos() {
            return correctos;
        }

        public List<String> getErrores() {
            return errores;
        }

        public List<String> getAvisos() {
            return avisos;
        }

        public Path getRegistro() {
            return registro;
        }
    }

    // ─── Aplicar ─────────────────────────────────────────────────────────

    public Informe aplicar(Plan plan, Historial historial, Progreso progreso) throws IOException {
        // Primero las carpetas (se mueven de golpe) y después los sueltos que van dentro de ellas.
        List<Propuesta> lista = plan.getPropuestas().stream().filter(Propuesta::seleccionada)
                .sorted(Comparator.comparing((Propuesta p) -> !p.getElemento().esCarpeta()))
                .toList();
        Informe informe = new Informe();
        try (Diario diario = historial.nuevo(plan.getOrigen())) {
            informe.registro = diario.getArchivo();
            Movedor movedor = new Movedor(diario, plan.getDestinoDuplicados(), informe);
            for (int i = 0; i < lista.size(); i++) {
                Propuesta p = lista.get(i);
                String nombre = p.getElemento().getNombre();
                progreso.avance(i, lista.size(), nombre);
                Path origen = p.getElemento().getRuta();
                if (!Archivos.existe(origen)) {
                    informe.errores.add(nombre + ": ya no está donde estaba");
                    continue;
                }
                try {
                    movedor.mover(origen, p.getDestino());
                    informe.correctos++;
                } catch (IOException | RuntimeException ex) {
                    informe.errores.add(nombre + ": " + Archivos.explicar(ex));
                }
            }
            progreso.avance(lista.size(), lista.size(), "");
            diario.terminar(informe.correctos, informe.errores.size());
        }
        return informe;
    }

    /** Hace los movimientos y los apunta en el diario. */
    private static final class Movedor {
        private final Diario diario;
        private final Path duplicados;
        private final Informe informe;

        Movedor(Diario diario, Path duplicados, Informe informe) {
            this.diario = diario;
            this.duplicados = duplicados;
            this.informe = informe;
        }

        void mover(Path origen, Path destino) throws IOException {
            crearCarpetas(destino.getParent());
            BasicFileAttributes a = Files.readAttributes(origen, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (a.isDirectory()) {
                if (Archivos.existe(destino)) {
                    if (Files.isDirectory(destino, LinkOption.NOFOLLOW_LINKS)) {
                        juntar(origen, destino);
                        return;
                    }
                    destino = Archivos.nombreLibre(destino);
                }
                try {
                    Files.move(origen, destino);
                    diario.movido(origen, destino);
                } catch (DirectoryNotEmptyException ex) {
                    // Va a otra unidad (otro disco, un USB): se mueve lo de dentro uno a uno.
                    Files.createDirectory(destino);
                    diario.carpetaCreada(destino);
                    juntar(origen, destino);
                }
            } else {
                if (Archivos.existe(destino)) {
                    if (duplicados != null && Archivos.mismoContenido(origen, destino)) {
                        destino = Archivos.nombreLibre(duplicados.resolve(origen.getFileName().toString()));
                        crearCarpetas(destino.getParent());
                    } else {
                        destino = Archivos.nombreLibre(destino);
                    }
                }
                Files.move(origen, destino);
                diario.movido(origen, destino);
            }
        }

        /** Mete el contenido de una carpeta dentro de otra que ya existe. */
        private void juntar(Path origen, Path destino) throws IOException {
            List<Path> hijos;
            try (Stream<Path> s = Files.list(origen)) {
                hijos = s.toList();
            }
            for (Path hijo : hijos) {
                try {
                    mover(hijo, destino.resolve(hijo.getFileName().toString()));
                } catch (IOException | RuntimeException ex) {
                    informe.errores.add(origen.getFileName() + "/" + hijo.getFileName() + ": " + Archivos.explicar(ex));
                }
            }
            if (Archivos.carpetaVacia(origen)) {
                Files.delete(origen);
                diario.carpetaBorrada(origen);
            }
        }

        /** Crea las carpetas que falten, apuntándolas para poder quitarlas al deshacer. */
        private void crearCarpetas(Path carpeta) throws IOException {
            if (carpeta == null || Files.isDirectory(carpeta)) {
                return;
            }
            crearCarpetas(carpeta.getParent());
            Files.createDirectory(carpeta);
            diario.carpetaCreada(carpeta);
        }
    }

    // ─── Deshacer ────────────────────────────────────────────────────────

    /** Devuelve todo a su sitio, en orden inverso, y quita las carpetas que se crearon (si están vacías). */
    public Informe deshacer(Ejecucion ejecucion, Historial historial, Progreso progreso) throws IOException {
        List<Operacion> operaciones = new ArrayList<>(ejecucion.getOperaciones());
        Collections.reverse(operaciones);
        Informe informe = new Informe();
        informe.registro = ejecucion.getArchivo();
        for (int i = 0; i < operaciones.size(); i++) {
            Operacion op = operaciones.get(i);
            progreso.avance(i, operaciones.size(), op.a().getFileName() == null ? "" : op.a().getFileName().toString());
            try {
                switch (op.tipo()) {
                    case MOVIDO -> devolver(op.a(), op.b(), informe);
                    case BORRADA -> Files.createDirectories(op.a());
                    case CREADA -> {
                        if (Files.isDirectory(op.a(), LinkOption.NOFOLLOW_LINKS) && Archivos.carpetaVacia(op.a())) {
                            Files.delete(op.a());
                        }
                    }
                }
            } catch (IOException | RuntimeException ex) {
                informe.errores.add(op.a().getFileName() + ": " + Archivos.explicar(ex));
            }
        }
        progreso.avance(operaciones.size(), operaciones.size(), "");
        historial.marcarDeshecha(ejecucion, informe.correctos, informe.errores.size());
        return informe;
    }

    private static void devolver(Path original, Path actual, Informe informe) throws IOException {
        if (!Archivos.existe(actual)) {
            informe.errores.add("No encuentro «" + actual.getFileName() + "» en " + actual.getParent()
                    + " (¿lo has movido o borrado después?)");
            return;
        }
        Path destino = original;
        if (Archivos.existe(original)) {
            destino = Archivos.nombreLibre(original);
            informe.avisos.add("Ya había otro «" + original.getFileName() + "»: lo he devuelto como «"
                    + destino.getFileName() + "»");
        }
        Files.createDirectories(destino.getParent());
        Archivos.mover(actual, destino);
        informe.correctos++;
    }
}
