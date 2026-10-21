package ordenalo.modelo;

import java.nio.file.Path;
import java.util.List;

/** Todas las propuestas para una carpeta, antes de aplicarlas. Nada se mueve hasta aplicar. */
public final class Plan {

    private final Path origen;
    private final Path destinoClase;
    private final Path destinoOtros;
    private boolean moverOtros;
    private final boolean buscarDuplicados;
    private final List<Propuesta> propuestas;
    private final int ignorados;
    private Path destinoDuplicados;

    public Plan(Path origen, Path destinoClase, Path destinoOtros, boolean moverOtros, boolean buscarDuplicados,
                List<Propuesta> propuestas, int ignorados) {
        this.origen = origen;
        this.destinoClase = destinoClase;
        this.destinoOtros = destinoOtros;
        this.moverOtros = moverOtros;
        this.buscarDuplicados = buscarDuplicados;
        this.propuestas = List.copyOf(propuestas);
        this.ignorados = ignorados;
    }

    public Path getOrigen() {
        return origen;
    }

    public Path getDestinoClase() {
        return destinoClase;
    }

    public Path getDestinoOtros() {
        return destinoOtros;
    }

    public boolean isMoverOtros() {
        return moverOtros;
    }

    public void setMoverOtros(boolean moverOtros) {
        this.moverOtros = moverOtros;
    }

    public boolean isBuscarDuplicados() {
        return buscarDuplicados;
    }

    public List<Propuesta> getPropuestas() {
        return propuestas;
    }

    /** Archivos ocultos, temporales o en descarga que no se han tocado. */
    public int getIgnorados() {
        return ignorados;
    }

    public Path getDestinoDuplicados() {
        return destinoDuplicados;
    }

    public void setDestinoDuplicados(Path destinoDuplicados) {
        this.destinoDuplicados = destinoDuplicados;
    }

    public int total() {
        return propuestas.size();
    }

    public int seMueven() {
        return (int) propuestas.stream().filter(Propuesta::seleccionada).count();
    }

    public int seQuedan() {
        return total() - seMueven();
    }

    public int copias() {
        return (int) propuestas.stream()
                .filter(p -> p.seleccionada() && p.categoria().getTipo() == Categoria.Tipo.DUPLICADO)
                .count();
    }

    /** Dudosos que nadie ha marcado todavía. */
    public int dudosasSinMarcar() {
        return (int) propuestas.stream()
                .filter(p -> p.dudosa() && p.getAccion() == Accion.MOVER && !p.seleccionada())
                .count();
    }
}
