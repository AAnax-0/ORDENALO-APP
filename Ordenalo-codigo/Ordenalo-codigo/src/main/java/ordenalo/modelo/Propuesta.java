package ordenalo.modelo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lo que se propone hacer con un elemento: a dónde va, por qué, y lo que el usuario haya cambiado.
 * <p>
 * Guarda por separado lo que decidió el programa (automático) y lo que eligió el usuario,
 * para poder recalcular los destinos sin perder sus elecciones.
 */
public final class Propuesta {

    private final Elemento elemento;

    // Lo que decidió el clasificador
    private Categoria categoriaAuto;
    private Confianza confianza;
    private List<String> motivos;
    private Elemento copiaDe;

    // Lo que ha elegido el usuario (null = lo que diga el programa)
    private Categoria categoriaUsuario;
    private String subcarpetaUsuario;
    private Boolean seleccionUsuario;

    // Lo que calcula el planificador
    private Accion accion = Accion.DEJAR;
    private String subcarpeta = "";
    private Path destino;
    private String nota;

    public Propuesta(Elemento elemento, Clasificacion clasificacion) {
        this.elemento = elemento;
        this.categoriaAuto = clasificacion.categoria();
        this.confianza = clasificacion.confianza();
        this.motivos = new ArrayList<>(clasificacion.motivos());
    }

    /** Este archivo es igual que otro de la misma carpeta: va a «Duplicados». */
    public void marcarComoCopia(Elemento original, Categoria duplicados) {
        this.copiaDe = original;
        this.categoriaAuto = duplicados;
        this.confianza = Confianza.ALTA;
        this.motivos = new ArrayList<>(List.of("Es una copia exacta de «" + original.getNombre() + "»"));
    }

    /** La categoría que manda: la elegida por el usuario o, si no, la automática. */
    public Categoria categoria() {
        return categoriaUsuario != null ? categoriaUsuario : categoriaAuto;
    }

    /** El usuario cambia a dónde va: se olvidan su subcarpeta y su marca anteriores. */
    public void elegirCategoria(Categoria categoria) {
        this.categoriaUsuario = categoria;
        this.subcarpetaUsuario = null;
        this.seleccionUsuario = null;
    }

    public boolean isElegidaPorUsuario() {
        return categoriaUsuario != null;
    }

    /** ¿Se moverá al pulsar «Aplicar»? Lo dudoso no, salvo que el usuario lo marque. */
    public boolean seleccionada() {
        if (accion != Accion.MOVER) {
            return false;
        }
        if (seleccionUsuario != null) {
            return seleccionUsuario;
        }
        return categoriaUsuario != null || confianza != Confianza.BAJA;
    }

    /** Clasificación poco segura que nadie ha revisado. */
    public boolean dudosa() {
        return categoriaUsuario == null && confianza == Confianza.BAJA;
    }

    /** Explicación para mostrar: por qué va ahí y cualquier aviso. */
    public String motivo() {
        String base = categoriaUsuario != null ? "Lo has elegido tú" : String.join(" · ", motivos);
        return nota == null ? base : base + " · " + nota;
    }

    public Elemento getElemento() {
        return elemento;
    }

    public Categoria getCategoriaAuto() {
        return categoriaAuto;
    }

    public Confianza getConfianza() {
        return confianza;
    }

    public List<String> getMotivos() {
        return List.copyOf(motivos);
    }

    public Elemento getCopiaDe() {
        return copiaDe;
    }

    public Categoria getCategoriaUsuario() {
        return categoriaUsuario;
    }

    public String getSubcarpetaUsuario() {
        return subcarpetaUsuario;
    }

    /** Subcarpeta elegida a mano ("" = directamente en la carpeta de la categoría). */
    public void setSubcarpetaUsuario(String subcarpetaUsuario) {
        this.subcarpetaUsuario = subcarpetaUsuario;
    }

    public Boolean getSeleccionUsuario() {
        return seleccionUsuario;
    }

    public void setSeleccionUsuario(Boolean seleccionUsuario) {
        this.seleccionUsuario = seleccionUsuario;
    }

    public Accion getAccion() {
        return accion;
    }

    public void setAccion(Accion accion) {
        this.accion = accion;
    }

    public String getSubcarpeta() {
        return subcarpeta;
    }

    public void setSubcarpeta(String subcarpeta) {
        this.subcarpeta = subcarpeta == null ? "" : subcarpeta;
    }

    /** Ruta completa donde acabará (null si se queda donde está). */
    public Path getDestino() {
        return destino;
    }

    public void setDestino(Path destino) {
        this.destino = destino;
    }

    public String getNota() {
        return nota;
    }

    public void setNota(String nota) {
        this.nota = nota;
    }

    @Override
    public String toString() {
        return elemento.getNombre() + " → " + (destino == null ? "(se queda)" : destino);
    }
}
