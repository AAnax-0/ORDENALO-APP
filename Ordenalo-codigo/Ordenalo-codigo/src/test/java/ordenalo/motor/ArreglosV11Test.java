package ordenalo.motor;

import ordenalo.modelo.Elemento;
import ordenalo.modelo.Plan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lo que se arregló en la 1.1 a partir de lo que pasó en un PC real. */
class ArreglosV11Test {

    @TempDir
    Path raiz;

    private static void crear(Path p) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, "contenido de " + p.getFileName());
    }

    private Escaner.Resultado escanear(Path carpeta) throws IOException {
        return Escaner.escanear(carpeta, List.of(), m -> { });
    }

    @Test
    void noTocaEnlacesNiUnionesDelSistema() throws IOException {
        Path docs = Files.createDirectories(raiz.resolve("Documentos"));
        Path musica = Files.createDirectories(raiz.resolve("Music"));
        Files.createSymbolicLink(docs.resolve("Mi música"), musica); // como las uniones ocultas de Windows
        crear(docs.resolve("notas.txt"));
        Files.createSymbolicLink(docs.resolve("atajo.txt"), docs.resolve("notas.txt"));

        List<String> vistos = escanear(docs).elementos().stream().map(Elemento::getNombre).toList();
        assertEquals(List.of("notas.txt"), vistos);
    }

    @Test
    void unProyectoDeNetBeansVaAProgramacionAunqueTengaXml() throws IOException {
        Path esc = Files.createDirectories(raiz.resolve("Escritorio"));
        Path p = esc.resolve("Practica1_ElementosBasicos");
        for (String f : List.of("build.xml", "manifest.mf", ".gitattributes", ".git/config", "nbproject/project.xml",
                "nbproject/build-impl.xml", "nbproject/private/private.xml", "src/tienda/SimuladorTienda.java")) {
            crear(p.resolve(f));
        }
        Elemento e = escanear(esc).elementos().get(0);
        assertTrue(e.tieneMarca(Elemento.MARCA_GIT));
        assertEquals("Programación", new Clasificador(Reglas.porDefecto()).clasificar(e).categoria().getNombre());
    }

    @Test
    void unaBovedaDeObsidianSeQuedaDondeEsta() throws IOException {
        Path esc = Files.createDirectories(raiz.resolve("Escritorio"));
        Path v = esc.resolve("Vault/CBrain");
        for (String f : List.of(".obsidian/app.json", ".obsidian/workspace.json", "WEB.md", "Interfaz.md")) {
            crear(v.resolve(f));
        }
        crear(esc.resolve("Vault/.obsidian/app.json"));
        Elemento e = escanear(esc).elementos().get(0);
        assertEquals("Dejar donde está", new Clasificador(Reglas.porDefecto()).clasificar(e).categoria().getNombre());
    }

    @Test
    void avisaAlMoverUnRepositorioGit() throws IOException {
        Path esc = Files.createDirectories(raiz.resolve("Escritorio"));
        crear(esc.resolve("Proyecto-Integrador-LM-2026/.git/HEAD"));
        crear(esc.resolve("Proyecto-Integrador-LM-2026/proyecto/index.html"));
        Plan plan = new Planificador(Reglas.porDefecto()).crear(esc, escanear(esc),
                raiz.resolve("DAM"), raiz.resolve("Ordenado"), true, true);
        assertTrue(plan.getPropuestas().get(0).motivo().contains("GitHub Desktop"), plan.getPropuestas().get(0).motivo());
    }

    @Test
    void elReparadorDevuelveLoQueMovioLaVersionAnteriorYNadaMas() throws IOException {
        Path docs = Files.createDirectories(raiz.resolve("Documentos"));
        Path musica = Files.createDirectories(raiz.resolve("Music"));
        Path enlace = Files.createSymbolicLink(docs.resolve("Mi música"), musica);
        Path enOtros = Files.createDirectories(docs.resolve("Ordenado/Otros")).resolve("Mi música");
        Path tarea = docs.resolve("tarea.docx");
        crear(tarea);
        Path tareaMovida = docs.resolve("DAM/tarea.docx");
        Files.createDirectories(tareaMovida.getParent());

        // Así lo dejó la 1.0: el enlace movido a Ordenado/Otros y apuntado en el historial
        Historial historial = new Historial(raiz.resolve("datos/historial"));
        try (Historial.Diario d = historial.nuevo(docs)) {
            Files.move(enlace, enOtros);
            d.movido(enlace, enOtros);
            Files.move(tarea, tareaMovida);
            d.movido(tarea, tareaMovida);
            d.terminar(2, 0);
        }

        List<Reparador.Arreglo> arreglos = Reparador.buscar(historial);
        assertEquals(1, arreglos.size());
        assertTrue(Reparador.reparar(arreglos, historial).isEmpty());
        assertTrue(Files.isSymbolicLink(docs.resolve("Mi música")), "el enlace vuelve a Documentos");
        assertTrue(Files.exists(tareaMovida), "lo demás de esa ordenación no se toca");
        assertTrue(Reparador.buscar(historial).isEmpty(), "no se ofrece dos veces");

        // Si luego se deshace la ordenación entera, no se queja del enlace ya devuelto
        Ejecutor.Informe informe = new Ejecutor().deshacer(historial.ultimaDeshacible().orElseThrow(), historial, (h, t, q) -> { });
        assertTrue(informe.getErrores().isEmpty(), informe.getErrores().toString());
        assertTrue(Files.exists(tarea));
        assertTrue(Files.isSymbolicLink(docs.resolve("Mi música")));
    }
}
