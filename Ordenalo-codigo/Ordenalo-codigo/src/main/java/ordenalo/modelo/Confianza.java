package ordenalo.modelo;

/** Lo seguro que está el programa de haber acertado. */
public enum Confianza {
    ALTA("Alta"),
    MEDIA("Media"),
    /** Lo dudoso no se marca para mover: lo decides tú. */
    BAJA("Baja");

    private final String etiqueta;

    Confianza(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
