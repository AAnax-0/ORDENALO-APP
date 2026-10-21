package ordenalo;

import ordenalo.ui.VentanaPrincipal;

/**
 * Ordénalo: organiza el escritorio y las carpetas de clase por asignaturas y tareas.
 * <p>
 * Sin argumentos abre la ventana. Con argumentos funciona por línea de comandos
 * (ver {@link Cli}), útil para probar las reglas o automatizar.
 */
public final class App {

    public static final String NOMBRE = "Ordénalo";
    public static final String VERSION = "1.1";

    private App() {
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            System.exit(Cli.ejecutar(args));
            return;
        }
        VentanaPrincipal.lanzar();
    }
}
