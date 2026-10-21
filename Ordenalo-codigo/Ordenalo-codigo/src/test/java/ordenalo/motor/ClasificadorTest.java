package ordenalo.motor;

import ordenalo.modelo.Clasificacion;
import ordenalo.modelo.Confianza;
import ordenalo.modelo.Elemento;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClasificadorTest {

    private final Reglas reglas = Reglas.porDefecto();
    private final Clasificador clasificador = new Clasificador(reglas);

    private Clasificacion de(String nombre) {
        return clasificador.clasificar(Elemento.archivo(Path.of(nombre), 100, 0));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "Tarea3_SI.docx                     | Sistemas Informáticos",
            "investigacion-sobre-sistemas-operativos.docx | Sistemas Informáticos",
            "SQLite trabajo.docx                | Bases de Datos",
            "consultas.sql                      | Bases de Datos",
            "Ejercicio1.java                    | Programación",
            "JavaScript apuntes.pdf             | Lenguajes de Marcas",
            "Comparativa IDEs IntelliJ.docx     | Entornos de Desarrollo",
            "Trabajo PRL puestos DAM.docx       | Itinerario para la Empleabilidad",
            "Captura de pantalla 2026-10-05.png | Capturas de pantalla",
            "IMG_20261003.jpg                   | Imágenes",
            "jdk-21_windows-x64_bin.exe         | Instaladores",
            "ubuntu-24.04-desktop-amd64.iso     | ISOs y máquinas virtuales",
            "Ed Sheeran - Perfect.mp3           | Música y audio",
            "enunciado tarea4.pdf               | Sin asignatura",
            "Brave.lnk                          | Dejar donde está",
    })
    void clasificaComoSeEspera(String nombre, String categoria) {
        assertEquals(categoria, de(nombre.trim()).categoria().getNombre());
    }

    @Test
    void loDudosoEsDudoso() {
        assertEquals(Confianza.BAJA, de("Apuntes Tema 2.pdf").confianza());
        assertEquals(Confianza.ALTA, de("Actividad 2 SI.docx").confianza());
    }

    @Test
    void lasCarpetasSeClasificanPorLoQueLlevanDentro() {
        Elemento proyecto = new Elemento(Path.of("cosas"), "cosas", true, 0, 0,
                Map.of("sql", 3, "docx", 1), 4, List.of(Texto.normalizarNombre("Tarea2 BD")), false);
        assertEquals("Bases de Datos", clasificador.clasificar(proyecto).categoria().getNombre());

        Elemento fotos = new Elemento(Path.of("fotos viaje"), "fotos viaje", true, 0, 0,
                Map.of("jpg", 9, "mp4", 1), 10, List.of(), false);
        assertEquals("Imágenes", clasificador.clasificar(fotos).categoria().getNombre());
    }

    @Test
    void lasCarpetasDeProgramasNoSeTocan() {
        Elemento idea = new Elemento(Path.of("IdeaProjects"), "IdeaProjects", true, 0, 0, Map.of("java", 40), 40, List.of(), false);
        assertEquals("Dejar donde está", clasificador.clasificar(idea).categoria().getNombre());
    }

    @Test
    void lasReglasSeGuardanYSeLeenIgual() {
        Reglas leidas = Reglas.desdeTexto(reglas.aTexto());
        assertEquals(reglas.getCategorias().size(), leidas.getCategorias().size());
        assertEquals(reglas.aTexto(), leidas.aTexto());
    }
}
