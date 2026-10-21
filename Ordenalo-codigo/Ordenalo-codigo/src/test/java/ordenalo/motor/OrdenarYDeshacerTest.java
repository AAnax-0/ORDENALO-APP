package ordenalo.motor;

import ordenalo.modelo.Plan;
import ordenalo.modelo.Propuesta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prueba de principio a fin: un escritorio desordenado se ordena y se deshace sin perder nada. */
class OrdenarYDeshacerTest {

    @TempDir
    Path raiz;

    private Path escritorio;
    private Path clase;
    private Path otros;
    private Historial historial;

    @BeforeEach
    void preparar() throws IOException {
        escritorio = Files.createDirectories(raiz.resolve("Escritorio"));
        clase = raiz.resolve("Documentos/DAM");
        otros = raiz.resolve("Documentos/Ordenado");
        historial = new Historial(raiz.resolve("datos/historial"));
        crear("Actividad 2 SI/instalacion.docx");
        crear("Actividad 2 SI/capturas/FileZilla.png");
        crear("actividad2 SI enlaces.txt");
        crear("tarea3-factura/src/Main.java");
        crear("Ejercicio1.java");
        crear("Captura de pantalla 2026-10-05.png");
        crear("Brave.lnk");
        crear("normas.pdf", "IGUAL");
        crear("normas (1).pdf", "IGUAL");
        crear("Apuntes Tema 2.pdf");
        // Ya existía una carpeta de Programación con otro nombre y un archivo que choca
        crear(clase.resolve("PROG/Ejercicios/Ejercicio1.java"), "otro contenido");
    }

    private void crear(String relativa) throws IOException {
        crear(relativa, "contenido de " + relativa);
    }

    private void crear(String relativa, String contenido) throws IOException {
        crear(escritorio.resolve(relativa), contenido);
    }

    private static void crear(Path p, String contenido) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, contenido, StandardCharsets.UTF_8);
    }

    private Plan plan() throws IOException {
        Escaner.Resultado r = Escaner.escanear(escritorio, List.of(clase, otros), m -> { });
        return new Planificador(Reglas.porDefecto()).crear(escritorio, r, clase, otros, true, true);
    }

    /** Ruta relativa → contenido de todos los archivos bajo la carpeta temporal. */
    private Map<String, String> foto() throws IOException {
        Map<String, String> mapa = new TreeMap<>();
        try (Stream<Path> s = Files.walk(raiz)) {
            for (Path p : s.filter(Files::isRegularFile).filter(p -> !p.startsWith(raiz.resolve("datos"))).toList()) {
                mapa.put(raiz.relativize(p).toString().replace('\\', '/'), Files.readString(p));
            }
        }
        return mapa;
    }

    @Test
    void elPlanPoneCadaCosaEnSuSitio() throws IOException {
        Plan plan = plan();
        assertEquals(clase.resolve("SI/Actividad 2 SI").toString(), destino(plan, "Actividad 2 SI").toString().replace("Sistemas Informáticos", "SI"));
        assertEquals(destino(plan, "Actividad 2 SI").resolve("actividad2 SI enlaces.txt"), destino(plan, "actividad2 SI enlaces.txt"),
                "el suelto se junta con la carpeta de su actividad");
        assertEquals(clase.resolve("PROG/Ejercicios/Ejercicio1 (2).java"), destino(plan, "Ejercicio1.java"),
                "usa la carpeta PROG que ya existía y no sobrescribe");
        assertEquals(otros.resolve("Duplicados/normas (1).pdf"), destino(plan, "normas (1).pdf"));
        assertFalse(propuesta(plan, "Brave.lnk").seleccionada(), "los accesos directos se quedan");
        assertFalse(propuesta(plan, "Apuntes Tema 2.pdf").seleccionada(), "lo dudoso no se mueve solo");
    }

    @Test
    void aplicarYDeshacerDejaTodoIgual() throws IOException {
        Map<String, String> antes = foto();
        Plan plan = plan();
        int aMover = plan.seMueven();

        Ejecutor.Informe informe = new Ejecutor().aplicar(plan, historial, (h, t, q) -> { });
        assertTrue(informe.getErrores().isEmpty(), informe.getErrores().toString());
        assertEquals(aMover, informe.getCorrectos());
        assertTrue(Files.exists(clase.resolve("PROG/tarea3-factura/src/Main.java")));
        assertTrue(Files.exists(escritorio.resolve("Brave.lnk")));
        assertEquals(antes.size(), foto().size(), "no se pierde ningún archivo");

        Historial.Ejecucion ultima = historial.ultimaDeshacible().orElseThrow();
        Ejecutor.Informe deshecho = new Ejecutor().deshacer(ultima, historial, (h, t, q) -> { });
        assertTrue(deshecho.getErrores().isEmpty(), deshecho.getErrores().toString());
        assertEquals(antes, foto(), "todo vuelve exactamente a donde estaba");
        assertFalse(Files.exists(otros), "las carpetas creadas se quitan al deshacer");
        assertTrue(historial.ultimaDeshacible().isEmpty());
    }

    @Test
    void juntarConUnaCarpetaQueYaExisteSeDeshaceBien() throws IOException {
        crear(clase.resolve("SI/Actividad 2 SI/ya estaba.docx"), "previo");
        Map<String, String> antes = foto();
        Plan plan = plan();
        new Ejecutor().aplicar(plan, historial, (h, t, q) -> { });
        assertTrue(Files.exists(clase.resolve("SI/Actividad 2 SI/ya estaba.docx")) || Files.exists(clase.resolve("Sistemas Informáticos/Actividad 2 SI/ya estaba.docx")));
        assertFalse(Files.exists(escritorio.resolve("Actividad 2 SI")));

        new Ejecutor().deshacer(historial.ultimaDeshacible().orElseThrow(), historial, (h, t, q) -> { });
        assertEquals(antes, foto());
    }

    private static Path destino(Plan plan, String nombre) {
        return propuesta(plan, nombre).getDestino();
    }

    private static Propuesta propuesta(Plan plan, String nombre) {
        return plan.getPropuestas().stream().filter(p -> p.getElemento().getNombre().equals(nombre)).findFirst().orElseThrow();
    }
}
