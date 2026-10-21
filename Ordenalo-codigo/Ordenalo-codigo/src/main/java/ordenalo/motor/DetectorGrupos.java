package ordenalo.motor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reconoce de qué tarea es algo por su nombre: «Tarea3_factura» → «Tarea 3»,
 * «UD2 act 1» → «Tema 2 - Actividad 1», «ejercicio5.java» → «Ejercicios».
 * Así los sueltos de una misma tarea acaban juntos en su carpeta.
 */
public final class DetectorGrupos {

    /**
     * @param clave    lo que identifica la tarea al comparar («tarea3», «u2/actividad1»)
     * @param etiqueta nombre de la carpeta que se crea si no existe («Tarea 3»)
     */
    public record Grupo(String clave, String etiqueta) {
    }

    private static final Map<String, String> TAREAS = new HashMap<>();
    private static final Set<String> UNIDADES = Set.of("tema", "temas", "unidad", "unidades", "ud", "ut", "unit");
    private static final Set<String> EJERCICIOS = Set.of("ejercicio", "ejercicios", "ej", "ejer", "ejerc", "exercise", "exercises");
    private static final Set<String> APUNTES = Set.of("apuntes", "teoria", "temario", "resumen", "resumenes", "diapositivas", "transparencias");
    private static final Set<String> EXAMENES = Set.of("examen", "examenes", "exam");
    /** Palabras que pueden ir entre «Tarea» y el número: «Tarea nº 3», «Tarea num 3». */
    private static final Set<String> RELLENO = Set.of("n", "no", "num", "numero", "nro");

    static {
        poner("Actividad", "actividad", "actividades", "act", "activ", "activity");
        poner("Tarea", "tarea", "tareas", "task");
        poner("Práctica", "practica", "practicas", "prac", "pract", "lab", "laboratorio");
        poner("Proyecto", "proyecto", "proy", "project");
        poner("Trabajo", "trabajo", "trab");
        poner("Examen", "examen", "exam");
        poner("Boletín", "boletin");
        poner("Relación", "relacion");
    }

    private DetectorGrupos() {
    }

    private static void poner(String etiqueta, String... palabras) {
        for (String p : palabras) {
            TAREAS.put(p, etiqueta);
        }
    }

    /** @param normalizado nombre pasado por {@link Texto#normalizarNombre(String)} */
    public static Optional<Grupo> detectar(String normalizado) {
        String[] t = normalizado.trim().split(" ");
        String tarea = null;
        int numeroTarea = -1;
        int unidad = -1;
        boolean ejercicios = false;
        boolean apuntes = false;
        boolean examenes = false;

        for (int i = 0; i < t.length; i++) {
            String palabra = t[i];
            if (palabra.isEmpty()) {
                continue;
            }
            Integer numero = numeroTras(t, i);
            if (tarea == null && numero != null && TAREAS.containsKey(palabra)) {
                tarea = TAREAS.get(palabra);
                numeroTarea = numero;
            } else if (unidad < 0 && numero != null && UNIDADES.contains(palabra)) {
                unidad = numero;
            } else if (EJERCICIOS.contains(palabra)) {
                ejercicios = true;
            } else if (EXAMENES.contains(palabra)) {
                examenes = true;
            } else if (APUNTES.contains(palabra)) {
                apuntes = true;
            }
        }

        if (tarea != null) {
            String etiqueta = tarea + " " + numeroTarea;
            String clave = Texto.sinTildes(tarea).toLowerCase(Locale.ROOT) + numeroTarea;
            if (unidad >= 0) {
                return Optional.of(new Grupo("u" + unidad + "/" + clave, "Tema " + unidad + " - " + etiqueta));
            }
            return Optional.of(new Grupo(clave, etiqueta));
        }
        if (unidad >= 0) {
            return Optional.of(new Grupo("u" + unidad, "Tema " + unidad));
        }
        if (examenes) {
            return Optional.of(new Grupo("examenes", "Exámenes"));
        }
        if (ejercicios) {
            return Optional.of(new Grupo("ejercicios", "Ejercicios"));
        }
        if (apuntes) {
            return Optional.of(new Grupo("apuntes", "Apuntes"));
        }
        return Optional.empty();
    }

    /** El número (de 1 a 3 cifras, para no confundirlo con un año) que va después de la palabra i. */
    private static Integer numeroTras(String[] t, int i) {
        int j = i + 1;
        if (j < t.length && RELLENO.contains(t[j])) {
            j++;
        }
        if (j < t.length && !t[j].isEmpty() && t[j].length() <= 3 && t[j].chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(t[j]);
        }
        return null;
    }
}
