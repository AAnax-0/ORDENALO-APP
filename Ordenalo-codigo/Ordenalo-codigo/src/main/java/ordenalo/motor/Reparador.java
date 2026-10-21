package ordenalo.motor;

import ordenalo.motor.Historial.Ejecucion;
import ordenalo.motor.Historial.Operacion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Arregla lo que movió por error la versión 1.0: enlaces y carpetas del sistema
 * (como «Mi música», «Mis imágenes» y «Mis vídeos», que Windows guarda ocultos en Documentos).
 * <p>
 * Busca en el historial movimientos cuyo destino es algo intocable y que todavía no se han
 * deshecho, y los devuelve a su sitio sin tocar nada más de esa ordenación.
 */
public final class Reparador {

    /** Algo que hay que devolver: dónde está ahora y dónde estaba. */
    public record Arreglo(Ejecucion ejecucion, Path dondeEsta, Path original) {
    }

    private Reparador() {
    }

    public static List<Arreglo> buscar(Historial historial) {
        List<Arreglo> lista = new ArrayList<>();
        for (Ejecucion e : historial.listar()) {
            if (e.getDeshecha() != null || !e.esDeEsteEquipo()) {
                continue;
            }
            for (Operacion op : e.getOperaciones()) {
                if (op.tipo() == Operacion.Tipo.MOVIDO && Archivos.existe(op.b()) && !Archivos.existe(op.a())
                        && Escaner.esIntocable(op.b())) {
                    lista.add(new Arreglo(e, op.b(), op.a()));
                }
            }
        }
        return lista;
    }

    /** @return los errores (vacío si todo ha ido bien) */
    public static List<String> reparar(List<Arreglo> arreglos, Historial historial) {
        List<String> errores = new ArrayList<>();
        for (Arreglo a : arreglos) {
            try {
                Files.createDirectories(a.original().getParent());
                Files.move(a.dondeEsta(), a.original()); // mueve el enlace en sí, no lo que hay detrás
                historial.marcarReparado(a.ejecucion(), a.original());
            } catch (IOException | RuntimeException ex) {
                errores.add(a.original().getFileName() + ": " + Archivos.explicar(ex));
            }
        }
        return errores;
    }
}
