package ordenalo;

import ordenalo.modelo.Accion;
import ordenalo.modelo.Plan;
import ordenalo.modelo.Propuesta;
import ordenalo.motor.CarpetasSistema;
import ordenalo.motor.Config;
import ordenalo.motor.Ejecutor;
import ordenalo.motor.Escaner;
import ordenalo.motor.Historial;
import ordenalo.motor.Planificador;
import ordenalo.motor.Reglas;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Uso por línea de comandos:
 * <pre>
 *   java -jar Ordenalo.jar --simular [carpeta]   muestra el plan sin mover nada
 *   java -jar Ordenalo.jar --aplicar [carpeta]   aplica lo que no es dudoso
 *   java -jar Ordenalo.jar --deshacer            deshace la última ordenación
 *   java -jar Ordenalo.jar --historial           lista las ordenaciones
 * </pre>
 */
public final class Cli {

    private Cli() {
    }

    public static int ejecutar(String[] args) {
        String modo = args[0].toLowerCase(Locale.ROOT);
        Map<String, String> opciones = new HashMap<>();
        List<String> sueltos = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--clase", "--otros" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("Falta la carpeta después de " + a);
                        return 2;
                    }
                    opciones.put(a, args[++i]);
                }
                case "--sin-otros", "--sin-duplicados" -> opciones.put(a, "si");
                default -> sueltos.add(a);
            }
        }
        try {
            Config config = Config.cargar();
            return switch (modo) {
                case "--simular", "--aplicar" -> ordenar(config, modo.equals("--aplicar"), opciones, sueltos);
                case "--deshacer" -> deshacer(config);
                case "--historial" -> historial(config);
                case "--ayuda", "--help", "-h", "/?" -> {
                    ayuda();
                    yield 0;
                }
                default -> {
                    System.err.println("No entiendo «" + args[0] + "».");
                    ayuda();
                    yield 2;
                }
            };
        } catch (IOException | RuntimeException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private static void ayuda() {
        System.out.println("""
                Ordénalo %s: organiza tus carpetas de clase.

                Sin nada, abre la ventana. Por línea de comandos:
                  --simular [carpeta]   enseña a dónde iría cada cosa, sin mover nada
                  --aplicar [carpeta]   mueve lo que no es dudoso (lo dudoso se queda)
                  --deshacer            deshace la última ordenación de este ordenador
                  --historial           lista las ordenaciones hechas

                Opciones de --simular y --aplicar:
                  --clase <carpeta>     dónde van las asignaturas
                  --otros <carpeta>     dónde va lo demás (imágenes, PDF, instaladores…)
                  --sin-otros           ordena solo lo de clase
                  --sin-duplicados      no busques copias repetidas

                Si no dices carpeta, se ordena el Escritorio.""".formatted(App.VERSION));
    }

    private static int ordenar(Config config, boolean aplicar, Map<String, String> opciones, List<String> sueltos)
            throws IOException {
        Path origen = (sueltos.isEmpty() ? CarpetasSistema.escritorio() : Path.of(sueltos.get(0))).toAbsolutePath().normalize();
        if (!Files.isDirectory(origen)) {
            System.err.println("No existe la carpeta " + origen);
            return 2;
        }
        if (CarpetasSistema.esPeligrosa(origen)) {
            System.err.println("Mejor no: " + origen + " es una carpeta del sistema o tu carpeta de usuario entera.");
            return 2;
        }
        if (opciones.containsKey("--clase")) {
            config.setDestinoClase(Path.of(opciones.get("--clase")).toAbsolutePath().normalize());
        }
        if (opciones.containsKey("--otros")) {
            config.setDestinoOtros(Path.of(opciones.get("--otros")).toAbsolutePath().normalize());
        }
        Reglas reglas = Reglas.cargar(config.archivoReglas());
        Escaner.Resultado escaneo = Escaner.escanear(origen, config.exclusionesPara(origen), m -> { });
        Plan plan = new Planificador(reglas).crear(origen, escaneo, config.getDestinoClase(), config.getDestinoOtros(),
                !opciones.containsKey("--sin-otros"), !opciones.containsKey("--sin-duplicados"));

        System.out.println("Ordénalo " + App.VERSION + (aplicar ? " · aplicando" : " · simulación (no se mueve nada)"));
        System.out.println("Carpeta:   " + plan.getOrigen());
        System.out.println("Clase:     " + plan.getDestinoClase());
        System.out.println("Lo demás:  " + plan.getDestinoOtros());
        System.out.println();
        for (Propuesta p : plan.getPropuestas()) {
            System.out.println(linea(p, plan));
        }
        System.out.println();
        System.out.println(plan.total() + " elementos · " + plan.seMueven() + " se mueven · " + plan.seQuedan()
                + " se quedan" + (plan.dudosasSinMarcar() > 0 ? " · " + plan.dudosasSinMarcar() + " dudosos (no se mueven)" : ""));

        if (!aplicar) {
            return 0;
        }
        Historial historial = new Historial(config.carpetaHistorial());
        Ejecutor.Informe informe = new Ejecutor().aplicar(plan, historial, (h, t, q) -> { });
        System.out.println("Movidos: " + informe.getCorrectos() + ". Para deshacer: --deshacer");
        informe.getErrores().forEach(e -> System.out.println("  No se pudo: " + e));
        return informe.getErrores().isEmpty() ? 0 : 1;
    }

    private static String linea(Propuesta p, Plan plan) {
        String marca = p.getAccion() == Accion.DEJAR ? " - " : p.seleccionada() ? "[x]" : "[ ]";
        String nombre = p.getElemento().getNombre() + (p.getElemento().esCarpeta() ? "/" : "");
        String destino;
        if (p.getDestino() == null) {
            destino = "(se queda)";
        } else {
            destino = relativo(p.getDestino().getParent(), plan) + "/";
        }
        String confianza = p.getAccion() == Accion.DEJAR ? "" : p.getConfianza().etiqueta();
        return String.format("%s %-40s → %-52s %-6s %s", marca, cortar(nombre, 40), cortar(destino, 52), confianza, p.motivo());
    }

    private static String relativo(Path carpeta, Plan plan) {
        for (Path raiz : List.of(plan.getDestinoClase(), plan.getDestinoOtros())) {
            if (carpeta.startsWith(raiz) && raiz.getParent() != null) {
                return raiz.getParent().relativize(carpeta).toString().replace('\\', '/');
            }
        }
        return carpeta.toString();
    }

    private static String cortar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static int deshacer(Config config) throws IOException {
        Historial historial = new Historial(config.carpetaHistorial());
        Optional<Historial.Ejecucion> ultima = historial.ultimaDeshacible();
        if (ultima.isEmpty()) {
            System.out.println("No hay nada que deshacer.");
            return 0;
        }
        Ejecutor.Informe informe = new Ejecutor().deshacer(ultima.get(), historial, (h, t, q) -> { });
        System.out.println("Devueltos a su sitio: " + informe.getCorrectos());
        informe.getAvisos().forEach(a -> System.out.println("  Aviso: " + a));
        informe.getErrores().forEach(e -> System.out.println("  No se pudo: " + e));
        return informe.getErrores().isEmpty() ? 0 : 1;
    }

    private static int historial(Config config) {
        List<Historial.Ejecucion> lista = new Historial(config.carpetaHistorial()).listar();
        if (lista.isEmpty()) {
            System.out.println("Todavía no has ordenado nada.");
        }
        for (Historial.Ejecucion e : lista) {
            System.out.printf("%s  %-50s %4d elementos  %s%n", e.getFecha().toString().replace('T', ' '),
                    e.getOrigen(), e.getElementos(), e.getDeshecha() != null ? "(deshecha)" : "");
        }
        return 0;
    }
}
