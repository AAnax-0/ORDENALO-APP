package ordenalo;

import javax.swing.JOptionPane;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;

/**
 * Punto de entrada del .jar.
 * <p>
 * Esta clase se compila para Java 8 a propósito: si alguien abre la aplicación con un Java
 * antiguo, en lugar de no pasar nada (o un error raro) le explicamos qué necesita.
 * El resto del programa está en {@link App} y usa Java 17.
 */
public final class Arranque {

    private static final int JAVA_MINIMO = 17;

    private Arranque() {
    }

    public static void main(String[] args) throws Exception {
        String version = System.getProperty("java.specification.version", "0");
        int mayor;
        try {
            mayor = version.startsWith("1.")
                    ? Integer.parseInt(version.substring(2))
                    : Integer.parseInt(version.split("\\.")[0]);
        } catch (NumberFormatException e) {
            mayor = 0;
        }

        if (mayor < JAVA_MINIMO) {
            String mensaje = "Ordénalo necesita Java " + JAVA_MINIMO + " o superior, y se ha abierto con Java " + version + ".\n\n"
                    + "• Si tienes IntelliJ IDEA, abre Ordenalo.bat: busca solo el Java que trae IntelliJ.\n"
                    + "• O instala Java 21 (gratis) desde https://adoptium.net";
            if (args.length > 0 || GraphicsEnvironment.isHeadless()) {
                System.err.println(mensaje);
            } else {
                JOptionPane.showMessageDialog(null, mensaje, "Ordénalo", JOptionPane.WARNING_MESSAGE);
            }
            System.exit(1);
        }

        try {
            Class.forName("ordenalo.App").getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            Throwable causa = e.getCause();
            if (causa instanceof Error) {
                throw (Error) causa;
            }
            throw (Exception) causa;
        }
    }
}
