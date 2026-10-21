package ordenalo.modelo;

import java.util.List;

/**
 * Lo que decide el clasificador para un elemento: a qué categoría va, con qué seguridad y por qué.
 */
public record Clasificacion(Categoria categoria, Confianza confianza, List<String> motivos) {

    public Clasificacion {
        motivos = List.copyOf(motivos);
    }
}
