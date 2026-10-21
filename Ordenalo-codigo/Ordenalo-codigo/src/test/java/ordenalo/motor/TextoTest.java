package ordenalo.motor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextoTest {

    @Test
    void separaPalabrasMayusculasYNumeros() {
        assertEquals(" tarea 3 si final ", Texto.normalizarNombre("Tarea3_SI-final"));
        assertEquals(" actividad si 2 ", Texto.normalizarNombre("ActividadSI2"));
        assertEquals(" programacion ", Texto.normalizarNombre("Programación"));
        assertEquals(" comparativa ides ", Texto.normalizarNombre("Comparativa IDEs"));
    }

    @Test
    void noPartePalabrasConocidas() {
        assertEquals(" ejercicios javascript ", Texto.normalizarNombre("EjerciciosJavaScript"));
        assertEquals(" trabajo sqlite ", Texto.normalizarNombre("TrabajoSQLite"));
        assertEquals(" patriota ", Texto.normalizarNombre("patriota"));
    }

    @Test
    void extensiones() {
        assertEquals("jpg", Texto.extension("Foto.JPG"));
        assertEquals("gz", Texto.extension("copia.tar.gz"));
        assertEquals("", Texto.extension("Tarea 3.1"));
        assertEquals("", Texto.extension(".gitignore"));
        assertEquals("Tarea 3", Texto.raiz("Tarea 3.docx"));
    }

    @Test
    void palabrasClave() {
        PalabraClave ejercicio = new PalabraClave("ejercicio");
        String n = Texto.normalizarNombre("Ejercicios tema 2");
        assertTrue(ejercicio.apareceEn(n, n.replace(" ", "")), "admite plural");

        PalabraClave java = new PalabraClave("java");
        String js = Texto.normalizarNombre("apuntes JavaScript");
        assertFalse(java.apareceEn(js, js.replace(" ", "")), "JavaScript no es Java");

        PalabraClave prog = new PalabraClave("programación");
        String pegado = Texto.normalizarNombre("tareaprogramacion");
        assertTrue(prog.apareceEn(pegado, pegado.replace(" ", "")), "las largas se encuentran pegadas");
    }

    @Test
    void nombresDeCarpetaValidosEnWindows() {
        assertEquals("Tarea 3- final", Texto.limpiarNombreCarpeta("Tarea 3: final"));
        assertEquals("con_", Texto.limpiarNombreCarpeta("con"), "CON es un nombre reservado en Windows");
        assertEquals("Tema 2/Ejercicios", Texto.limpiarRuta("Tema 2\\..\\Ejercicios/"));
    }
}
