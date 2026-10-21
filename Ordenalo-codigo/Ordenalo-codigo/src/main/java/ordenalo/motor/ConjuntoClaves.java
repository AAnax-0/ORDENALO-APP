package ordenalo.motor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Las palabras clave de una categoría, preparadas para buscarlas rápido en muchos nombres:
 * primero se prueba una única expresión regular con todas y solo si hay suerte se mira cuáles son.
 */
public final class ConjuntoClaves {

    public static final ConjuntoClaves VACIO = new ConjuntoClaves(List.of());

    private final List<PalabraClave> claves = new ArrayList<>();
    private final List<PalabraClave> compactas = new ArrayList<>();
    private final Pattern combinado;

    public ConjuntoClaves(Collection<String> palabras) {
        Set<String> vistas = new HashSet<>();
        List<String> largas = new ArrayList<>();
        List<String> cortas = new ArrayList<>();
        for (String p : palabras) {
            PalabraClave clave = new PalabraClave(p);
            if (clave.getTexto().isEmpty() || !vistas.add(clave.getTexto())) {
                continue;
            }
            claves.add(clave);
            if (clave.getCompacta() != null) {
                compactas.add(clave);
            }
            (clave.getTexto().length() >= 3 ? largas : cortas).add(Pattern.quote(clave.getTexto()));
        }
        if (claves.isEmpty()) {
            combinado = null;
            return;
        }
        StringBuilder sb = new StringBuilder("(?<= )(?:");
        if (!largas.isEmpty()) {
            sb.append("(?:").append(String.join("|", largas)).append(")(?:s|es)?");
        }
        if (!cortas.isEmpty()) {
            if (!largas.isEmpty()) {
                sb.append('|');
            }
            sb.append(String.join("|", cortas));
        }
        sb.append(")(?= )");
        combinado = Pattern.compile(sb.toString());
    }

    public boolean isVacio() {
        return claves.isEmpty();
    }

    public int tamano() {
        return claves.size();
    }

    /** ¿Aparece alguna de las palabras en el nombre? */
    public boolean algunaEn(String normalizado, String compacto) {
        if (combinado == null) {
            return false;
        }
        if (combinado.matcher(normalizado).find()) {
            return true;
        }
        for (PalabraClave clave : compactas) {
            if (compacto.contains(clave.getCompacta())) {
                return true;
            }
        }
        return false;
    }

    /** Las palabras (tal como se escribieron en la regla) que aparecen en el nombre. */
    public List<String> todasEn(String normalizado, String compacto) {
        if (!algunaEn(normalizado, compacto)) {
            return List.of();
        }
        List<String> encontradas = new ArrayList<>();
        for (PalabraClave clave : claves) {
            if (clave.apareceEn(normalizado, compacto)) {
                encontradas.add(clave.getOriginal());
            }
        }
        return encontradas;
    }
}
