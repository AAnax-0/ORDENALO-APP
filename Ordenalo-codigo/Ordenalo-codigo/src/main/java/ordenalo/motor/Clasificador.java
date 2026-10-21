package ordenalo.motor;

import ordenalo.modelo.Categoria;
import ordenalo.modelo.Clasificacion;
import ordenalo.modelo.Confianza;
import ordenalo.modelo.Elemento;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Decide a qué categoría va cada elemento.
 * <p>
 * Orden de decisión:
 * <ol>
 *   <li>Accesos directos y carpetas de programas: no se tocan.</li>
 *   <li>Tipos que mandan siempre (instaladores, ISOs).</li>
 *   <li>Asignaturas y temas personales, por puntos: palabras del nombre (10 cada una),
 *       extensión (6), tipos de archivo de dentro (4-8) y nombres de dentro (3 cada uno, máx. 9).</li>
 *   <li>Si parece de clase («Tarea 3») pero no se sabe de qué: «Sin asignatura», dudoso.</li>
 *   <li>Si no, por tipo de archivo (imágenes, PDF…).</li>
 * </ol>
 */
public final class Clasificador {

    private static final int PUNTOS_NOMBRE = 10;
    private static final int PUNTOS_EXTENSION = 6;
    private static final int PUNTOS_NOMBRE_DENTRO = 3;
    private static final int MAX_PUNTOS_NOMBRES_DENTRO = 9;
    private static final int MINIMO_PARA_ELEGIR = 6;

    private static final Set<String> ACCESOS_DIRECTOS = Set.of("lnk", "url", "desktop", "webloc", "website", "pif");
    private static final Set<String> AUDIO = Set.of("mp3", "flac", "wav", "ogg", "m4a", "aac", "wma", "opus");

    /** Carpetas del sistema o de programas que es mejor no mover nunca. */
    private static final Set<String> CARPETAS_PROTEGIDAS = new HashSet<>();

    static {
        String[] nombres = {
                "Desktop", "Escritorio", "Documents", "Documentos", "Mis documentos", "Downloads", "Descargas",
                "Pictures", "Imágenes", "Mis imágenes", "Music", "Música", "Mi música", "Videos", "Vídeos", "Mis vídeos",
                "OneDrive", "Google Drive", "Mi unidad", "Dropbox", "iCloud Drive", "AppData", "Application Data",
                "Saved Games", "Juegos guardados", "Contacts", "Contactos", "Favorites", "Favoritos", "Links", "Vínculos",
                "Searches", "Búsquedas", "3D Objects", "Objetos 3D", "IdeaProjects", "NetBeansProjects",
                "My Music", "My Pictures", "My Videos", "Mis documentos", "Plantillas", "Templates",
                "eclipse-workspace", "workspace", "AndroidStudioProjects", "Visual Studio 2019", "Visual Studio 2022",
                "Visual Studio Code", "GitHub", "source", "repos", "Zoom", "WindowsPowerShell", "PowerShell",
                "Custom Office Templates", "Plantillas personalizadas de Office", "OneNote Notebooks",
                "Blocs de notas de OneNote", "My Games", "Mis juegos", "IISExpress", "My Web Sites", "Outlook Files",
                "Archivos de Outlook", "SQL Server Management Studio", "Arduino", "Processing", "Fax",
                "Scanned Documents", "Documentos escaneados", "My Shapes", "Mis formas", "Virtual Machines",
                "VirtualBox VMs", "snap", "anaconda3", "miniconda3", "Calibre Library", "Biblioteca de calibre"
        };
        for (String n : nombres) {
            CARPETAS_PROTEGIDAS.add(Texto.normalizarClave(n));
        }
    }

    private final Reglas reglas;

    public Clasificador(Reglas reglas) {
        this.reglas = reglas;
    }

    public static boolean esCarpetaProtegida(String nombre) {
        return CARPETAS_PROTEGIDAS.contains(Texto.normalizarClave(nombre));
    }

    public Clasificacion clasificar(Elemento e) {
        // 1. Lo que no se toca
        if (!e.esCarpeta() && ACCESOS_DIRECTOS.contains(e.getExtension())) {
            return new Clasificacion(reglas.dejar(), Confianza.ALTA, List.of("Acceso directo: se queda donde está"));
        }
        if (e.esCarpeta() && esCarpetaProtegida(e.getNombre())) {
            return new Clasificacion(reglas.dejar(), Confianza.ALTA,
                    List.of("Carpeta de un programa o del sistema: mejor no tocarla"));
        }
        if (e.esCarpeta() && e.tieneMarca(Elemento.MARCA_ORDENALO)) {
            return new Clasificacion(reglas.dejar(), Confianza.ALTA, List.of("Es el propio Ordénalo"));
        }
        if (e.esCarpeta() && e.tieneMarca(Elemento.MARCA_OBSIDIAN)) {
            return new Clasificacion(reglas.dejar(), Confianza.ALTA,
                    List.of("Es una bóveda de Obsidian: si la mueves, tendrás que volver a abrirla en Obsidian (elige tú si la mueves)"));
        }

        // 2. Tipos que mandan aunque el nombre diga otra cosa (un instalador nunca es un trabajo)
        if (!e.esCarpeta()) {
            for (Categoria c : reglas.tiposDeArchivo()) {
                if (c.isSoloPorTipo() && c.getExtensiones().contains(e.getExtension())) {
                    return new Clasificacion(c, Confianza.ALTA, List.of("Es un archivo ." + e.getExtension()));
                }
            }
        }

        // 3. Asignaturas y temas personales, por puntos
        List<Puntos> puntuaciones = new ArrayList<>();
        for (Categoria c : reglas.tematicas()) {
            Puntos p = puntuar(c, e);
            if (p.total() > 0) {
                puntuaciones.add(p);
            }
        }
        // Orden estable: con empate, gana la que va antes en las reglas.
        puntuaciones.sort(Comparator.comparingInt(Puntos::total).reversed()
                .thenComparing(Comparator.comparingInt((Puntos p) -> p.nombre).reversed()));

        if (!puntuaciones.isEmpty() && puntuaciones.get(0).total() >= MINIMO_PARA_ELEGIR) {
            Puntos mejor = puntuaciones.get(0);
            Puntos segunda = puntuaciones.size() > 1 ? puntuaciones.get(1) : null;
            int margen = mejor.total() - (segunda == null ? 0 : segunda.total());
            Confianza confianza;
            if (mejor.nombre > 0 && margen >= 5) {
                confianza = Confianza.ALTA;
            } else if (margen >= 3) {
                confianza = Confianza.MEDIA;
            } else {
                confianza = Confianza.BAJA;
            }
            List<String> motivos = new ArrayList<>(mejor.motivos);
            if (segunda != null && margen < 5) {
                motivos.add("también podría ser de " + segunda.categoria.getNombre());
            }
            return new Clasificacion(mejor.categoria, confianza, motivos);
        }

        // 4. Parece de clase, pero no sabemos de qué asignatura
        Optional<DetectorGrupos.Grupo> grupo = DetectorGrupos.detectar(e.normalizado());
        if (grupo.isPresent()) {
            return new Clasificacion(reglas.sinAsignatura(), Confianza.BAJA,
                    List.of("Parece de clase («" + grupo.get().etiqueta() + "»), pero no sé de qué asignatura: elígela tú"));
        }

        // 5. Archivos: por tipo
        if (!e.esCarpeta()) {
            for (Categoria c : reglas.tiposDeArchivo()) {
                if (coincideTipo(c, e)) {
                    String motivo = c.getClaves().isVacio()
                            ? "Es un archivo ." + e.getExtension()
                            : "Por el nombre y la extensión (." + e.getExtension() + ")";
                    return new Clasificacion(c, Confianza.ALTA, List.of(motivo));
                }
            }
            String motivo = e.getExtension().isEmpty()
                    ? "Archivo sin extensión"
                    : "Archivo ." + e.getExtension() + " que no sé clasificar";
            return new Clasificacion(reglas.otrosArchivos(), Confianza.MEDIA, List.of(motivo));
        }

        // 6. Carpetas: por el tipo de archivo que más hay dentro
        if (e.getArchivosDentro() == 0) {
            return new Clasificacion(reglas.dejar(), Confianza.BAJA, List.of("Carpeta vacía"));
        }
        Categoria dominante = null;
        int maximo = 0;
        for (Categoria c : reglas.tiposDeArchivo()) {
            if (!c.getClaves().isVacio()) {
                continue; // las que dependen del nombre (capturas) no cuentan aquí
            }
            int n = contarDentro(c, e);
            if (n > maximo) {
                maximo = n;
                dominante = c;
            }
        }
        if (dominante != null && e.getArchivosDentro() >= 3 && maximo * 10 >= e.getArchivosDentro() * 7) {
            return new Clasificacion(dominante, Confianza.MEDIA,
                    List.of("Casi todo lo de dentro es de «" + dominante.getNombre() + "» ("
                            + maximo + " de " + e.getArchivosDentro() + ")"));
        }
        return new Clasificacion(reglas.dejar(), Confianza.BAJA,
                List.of("No sé qué es: elige tú a dónde va, o déjala aquí"));
    }

    private boolean coincideTipo(Categoria c, Elemento e) {
        if (!c.getExtensiones().contains(e.getExtension())) {
            return false;
        }
        return c.getClaves().isVacio() || c.getClaves().algunaEn(e.normalizado(), e.compacto());
    }

    private static int contarDentro(Categoria c, Elemento e) {
        int n = 0;
        for (String ext : c.getExtensiones()) {
            n += e.getExtensionesDentro().getOrDefault(ext, 0);
        }
        return n;
    }

    private Puntos puntuar(Categoria c, Elemento e) {
        Puntos p = new Puntos(c);

        List<String> enNombre = c.getClaves().todasEn(e.normalizado(), e.compacto());
        if (AUDIO.contains(e.getExtension())) {
            // En canciones las siglas cortas suelen ser casualidad («Ed Sheeran» no es Entornos de Desarrollo).
            enNombre = enNombre.stream().filter(k -> Texto.normalizarClave(k).length() > 3).toList();
        }
        if (!enNombre.isEmpty()) {
            p.nombre = PUNTOS_NOMBRE * enNombre.size();
            p.motivos.add("El nombre incluye " + Texto.citar(enNombre));
        }

        if (!e.esCarpeta() && c.getExtensiones().contains(e.getExtension())) {
            p.extension = PUNTOS_EXTENSION;
            p.motivos.add((p.motivos.isEmpty() ? "Es un archivo ." : "es un archivo .") + e.getExtension());
        }

        if (e.esCarpeta() && e.getArchivosDentro() > 0 && !c.getExtensiones().isEmpty()) {
            int n = 0;
            int mayor = 0;
            String principal = null;
            for (String ext : c.getExtensiones()) {
                int k = e.getExtensionesDentro().getOrDefault(ext, 0);
                n += k;
                if (k > mayor) {
                    mayor = k;
                    principal = ext;
                }
            }
            if (n > 0) {
                p.contenido = 4 + Math.round(4f * n / e.getArchivosDentro());
                String texto = n == 1 ? "1 archivo ." + principal : n + " archivos ." + principal + (n > mayor ? " y similares" : "");
                p.motivos.add((p.motivos.isEmpty() ? "Dentro hay " : "dentro hay ") + texto);
            }
        }

        if (e.esCarpeta() && !c.getClaves().isVacio()) {
            Set<String> encontradas = new LinkedHashSet<>();
            for (String hijo : e.getNombresDentro()) {
                encontradas.addAll(c.getClaves().todasEn(hijo, hijo.replace(" ", "")));
                if (encontradas.size() >= 3) {
                    break;
                }
            }
            encontradas.removeAll(enNombre);
            if (!encontradas.isEmpty()) {
                p.nombresDentro = Math.min(MAX_PUNTOS_NOMBRES_DENTRO, PUNTOS_NOMBRE_DENTRO * encontradas.size());
                p.motivos.add((p.motivos.isEmpty() ? "Dentro hay archivos con " : "dentro hay archivos con ")
                        + Texto.citar(encontradas));
            }
        }
        return p;
    }

    /** Puntos de una categoría para un elemento, con la explicación. */
    private static final class Puntos {
        final Categoria categoria;
        final List<String> motivos = new ArrayList<>();
        int nombre;
        int extension;
        int contenido;
        int nombresDentro;

        Puntos(Categoria categoria) {
            this.categoria = categoria;
        }

        int total() {
            return nombre + extension + contenido + nombresDentro;
        }
    }
}
