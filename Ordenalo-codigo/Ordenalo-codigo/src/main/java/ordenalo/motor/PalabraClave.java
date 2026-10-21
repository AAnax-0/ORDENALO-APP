package ordenalo.motor;

import java.util.regex.Pattern;

/**
 * Una palabra clave de una regla («bases de datos», «SI», «sqlite»…).
 * <p>
 * Aparece en un nombre si está como palabra completa (admitiendo plural: «ejercicio» encuentra
 * «ejercicios»). Las palabras largas se buscan también pegadas a otras, para encontrar «programacion»
 * dentro de «TareaProgramacion».
 */
public final class PalabraClave {

    private final String original;
    private final String texto;
    private final Pattern patron;
    private final String compacta;

    public PalabraClave(String original) {
        this.original = original.trim();
        this.texto = Texto.normalizarClave(original);
        String plural = texto.length() >= 3 ? "(?:s|es)?" : "";
        this.patron = texto.isEmpty() ? null : Pattern.compile("(?<= )" + Pattern.quote(texto) + plural + "(?= )");
        String sinEspacios = texto.replace(" ", "");
        this.compacta = sinEspacios.length() >= 6 ? sinEspacios : null;
    }

    /**
     * @param normalizado nombre pasado por {@link Texto#normalizarNombre(String)}
     * @param compacto    el mismo nombre sin espacios
     */
    public boolean apareceEn(String normalizado, String compacto) {
        if (patron == null) {
            return false;
        }
        if (patron.matcher(normalizado).find()) {
            return true;
        }
        return compacta != null && compacto.contains(compacta);
    }

    public String getOriginal() {
        return original;
    }

    public String getTexto() {
        return texto;
    }

    /** La palabra sin espacios si es lo bastante larga para buscarla pegada, o null. */
    public String getCompacta() {
        return compacta;
    }
}
