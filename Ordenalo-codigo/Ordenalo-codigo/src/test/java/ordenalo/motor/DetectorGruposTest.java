package ordenalo.motor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectorGruposTest {

    private static String etiqueta(String nombre) {
        return DetectorGrupos.detectar(Texto.normalizarNombre(nombre)).map(DetectorGrupos.Grupo::etiqueta).orElse("");
    }

    @Test
    void tareasYActividades() {
        assertEquals("Tarea 3", etiqueta("tarea3-factura"));
        assertEquals("Tarea 3", etiqueta("Tarea nº 3"));
        assertEquals("Actividad 2", etiqueta("Actividad 2 SI"));
        assertEquals("Actividad 2", etiqueta("act2_capturas"));
        assertEquals("Práctica 1", etiqueta("practica 1 entornos"));
        assertEquals("Actividad 2", etiqueta("Actividad 2.1"), "las subactividades van con su actividad");
    }

    @Test
    void temasYEjercicios() {
        assertEquals("Tema 2 - Actividad 1", etiqueta("UD2_act1_BBDD"));
        assertEquals("Tema 4", etiqueta("UT04 apuntes"));
        assertEquals("Ejercicios", etiqueta("Ejercicio5.java"));
        assertEquals("Tema 2", etiqueta("ejercicios tema 2"));
        assertEquals("Exámenes", etiqueta("examen final"));
    }

    @Test
    void noConfundeAnios() {
        assertTrue(etiqueta("Proyecto-Integrador-LM-2026").isEmpty());
        assertTrue(etiqueta("Captura de pantalla 2026-10-05").isEmpty());
    }

    @Test
    void mismaClaveParaLaMismaTarea() {
        String a = DetectorGrupos.detectar(Texto.normalizarNombre("Tarea-3 SI")).orElseThrow().clave();
        String b = DetectorGrupos.detectar(Texto.normalizarNombre("Tarea3_SI_capturas")).orElseThrow().clave();
        assertEquals(a, b);
    }
}
