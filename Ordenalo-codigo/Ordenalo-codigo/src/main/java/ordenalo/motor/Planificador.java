package ordenalo.motor;

import ordenalo.modelo.Accion;
import ordenalo.modelo.Categoria;
import ordenalo.modelo.Categoria.Tipo;
import ordenalo.modelo.Elemento;
import ordenalo.modelo.Plan;
import ordenalo.modelo.Propuesta;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Convierte lo que hay en una carpeta en un plan: a dónde iría cada cosa.
 * <p>
 * Además de clasificar, se encarga de:
 * <ul>
 *   <li>Usar las carpetas que ya tengas (si en tu carpeta de clase hay una «PROG», la usa para Programación).</li>
 *   <li>Juntar los sueltos con su tarea («tarea3_capturas.docx» va dentro de la carpeta «tarea3-factura»).</li>
 *   <li>Detectar copias repetidas.</li>
 *   <li>Evitar choques de nombres (nunca se sobrescribe nada).</li>
 * </ul>
 */
public final class Planificador {

    private static final long MAX_TAMANO_HUELLA = 512L * 1024 * 1024;
    private static final Pattern PARECE_COPIA = Pattern.compile(
            "(?iu).*(\\(\\d+\\)|\\s-\\s(copia|copy)(\\s\\(\\d+\\))?|\\bcopia\\b|\\bcopy\\b)\\s*$|^(copia de|copy of)\\b.*");

    private final Reglas reglas;
    private final Clasificador clasificador;
    private final Map<String, Boolean> comparaciones = new HashMap<>();

    public Planificador(Reglas reglas) {
        this.reglas = reglas;
        this.clasificador = new Clasificador(reglas);
    }

    public Reglas getReglas() {
        return reglas;
    }

    public Plan crear(Path origen, Escaner.Resultado escaneo, Path destinoClase, Path destinoOtros,
                      boolean moverOtros, boolean buscarDuplicados) {
        List<Propuesta> propuestas = new ArrayList<>();
        for (Elemento e : escaneo.elementos()) {
            propuestas.add(new Propuesta(e, clasificador.clasificar(e)));
        }
        if (buscarDuplicados) {
            marcarDuplicados(propuestas);
        }
        Plan plan = new Plan(origen.toAbsolutePath().normalize(), destinoClase.toAbsolutePath().normalize(),
                destinoOtros.toAbsolutePath().normalize(), moverOtros, buscarDuplicados, propuestas,
                escaneo.ignorados());
        recalcular(plan);
        return plan;
    }

    // ─── Copias repetidas ────────────────────────────────────────────────

    private void marcarDuplicados(List<Propuesta> propuestas) {
        Map<Long, List<Propuesta>> porTamano = new HashMap<>();
        for (Propuesta p : propuestas) {
            Elemento e = p.getElemento();
            if (e.esCarpeta() || e.getTamano() == 0 || e.getTamano() > MAX_TAMANO_HUELLA
                    || p.getCategoriaAuto().getTipo() == Tipo.DEJAR) {
                continue;
            }
            porTamano.computeIfAbsent(e.getTamano(), k -> new ArrayList<>()).add(p);
        }
        Comparator<Propuesta> masOriginal = Comparator
                .comparing((Propuesta p) -> pareceCopia(p.getElemento().getNombre()))
                .thenComparingInt(p -> p.getElemento().getNombre().length())
                .thenComparingLong(p -> p.getElemento().getModificado());

        for (List<Propuesta> mismoTamano : porTamano.values()) {
            if (mismoTamano.size() < 2) {
                continue;
            }
            Map<String, List<Propuesta>> porHuella = new HashMap<>();
            for (Propuesta p : mismoTamano) {
                String huella = Archivos.huella(p.getElemento().getRuta());
                if (huella != null) {
                    porHuella.computeIfAbsent(huella, k -> new ArrayList<>()).add(p);
                }
            }
            for (List<Propuesta> iguales : porHuella.values()) {
                if (iguales.size() < 2) {
                    continue;
                }
                iguales.sort(masOriginal);
                Elemento original = iguales.get(0).getElemento();
                for (int i = 1; i < iguales.size(); i++) {
                    iguales.get(i).marcarComoCopia(original, reglas.duplicados());
                }
            }
        }
    }

    static boolean pareceCopia(String nombre) {
        return PARECE_COPIA.matcher(Texto.raiz(nombre)).matches();
    }

    // ─── Destinos ────────────────────────────────────────────────────────

    /** Recalcula a dónde va cada cosa (por ejemplo, después de que el usuario cambie algo). */
    public void recalcular(Plan plan) {
        Contexto ctx = new Contexto(plan);
        plan.setDestinoDuplicados(ctx.carpetaDe(reglas.duplicados()));

        // 1) ¿Se mueve o se queda?
        for (Propuesta p : plan.getPropuestas()) {
            Categoria c = p.categoria();
            p.setNota(null);
            p.setDestino(null);
            p.setSubcarpeta("");
            if (c.getTipo() == Tipo.DEJAR) {
                p.setAccion(Accion.DEJAR);
                continue;
            }
            boolean noEsDeClase = c.getTipo() == Tipo.ARCHIVO || c.getTipo() == Tipo.PERSONAL;
            if (noEsDeClase && !plan.isMoverOtros() && !p.isElegidaPorUsuario()) {
                dejar(p, "no es de clase y tienes desactivado «Ordenar también lo demás»");
                continue;
            }
            p.setAccion(Accion.MOVER);
        }

        // 2) Carpetas de tareas que van a llegar: los sueltos de la misma tarea irán dentro
        Map<Categoria, Map<String, Path>> carpetasQueLlegan = new HashMap<>();
        for (Propuesta p : plan.getPropuestas()) {
            if (p.getAccion() != Accion.MOVER || !p.getElemento().esCarpeta()
                    || p.categoria().getTipo() != Tipo.ASIGNATURA || p.getSubcarpetaUsuario() != null) {
                continue;
            }
            Path base = ctx.carpetaDe(p.categoria());
            DetectorGrupos.detectar(p.getElemento().normalizado()).ifPresent(g ->
                    registrar(carpetasQueLlegan.computeIfAbsent(p.categoria(), k -> new HashMap<>()),
                            g.clave(), base.resolve(p.getElemento().getNombre())));
        }

        // 3) Destino final de cada cosa
        for (Propuesta p : plan.getPropuestas()) {
            if (p.getAccion() != Accion.MOVER) {
                continue;
            }
            Categoria c = p.categoria();
            Elemento e = p.getElemento();
            Path base = ctx.carpetaDe(c);
            if (c.getTipo() == Tipo.ASIGNATURA && !c.isIntegrada() && base.getFileName() != null
                    && !Texto.mismoNombre(base.getFileName().toString(), Texto.limpiarRuta(c.getCarpeta()))) {
                anotar(p, "usa tu carpeta «" + base.getFileName() + "»");
            }

            String sub = "";
            if (p.getSubcarpetaUsuario() != null) {
                sub = Texto.limpiarRuta(p.getSubcarpetaUsuario());
            } else if (c.getTipo() == Tipo.ASIGNATURA && !e.esCarpeta()) {
                Optional<DetectorGrupos.Grupo> grupo = DetectorGrupos.detectar(e.normalizado());
                if (grupo.isPresent()) {
                    String clave = grupo.get().clave();
                    Path carpetaTarea = ctx.tareasExistentes(c).get(clave);
                    if (carpetaTarea == null) {
                        carpetaTarea = carpetasQueLlegan.getOrDefault(c, Map.of()).get(clave);
                    }
                    sub = carpetaTarea != null
                            ? base.relativize(carpetaTarea).toString().replace('\\', '/')
                            : Texto.limpiarRuta(grupo.get().etiqueta());
                }
            }
            p.setSubcarpeta(sub);

            Path destino = (sub.isEmpty() ? base : base.resolve(sub)).resolve(e.getNombre()).normalize();
            Path actual = e.getRuta().toAbsolutePath().normalize();
            if (destino.equals(actual)) {
                dejar(p, "ya está en su sitio");
                continue;
            }
            if (e.esCarpeta() && destino.startsWith(actual)) {
                dejar(p, "no se puede meter una carpeta dentro de sí misma");
                continue;
            }
            destino = resolverConflicto(p, destino, ctx);
            p.setDestino(destino);
            ctx.reservar(destino);
            if (e.tieneMarca(Elemento.MARCA_GIT)) {
                anotar(p, "es un repositorio Git: si lo usas en GitHub Desktop, tendrás que volver a localizarlo");
            }
        }
    }

    private static void anotar(Propuesta p, String texto) {
        p.setNota(p.getNota() == null ? texto : p.getNota() + " · " + texto);
    }

    private static void dejar(Propuesta p, String nota) {
        p.setAccion(Accion.DEJAR);
        p.setDestino(null);
        p.setNota(nota);
    }

    /** Si ya hay algo con ese nombre: se junta (carpetas), va a Duplicados (copia idéntica) o se renombra. */
    private Path resolverConflicto(Propuesta p, Path destino, Contexto ctx) {
        Elemento e = p.getElemento();
        boolean reservado = ctx.reservado(destino);
        if (!reservado && !Archivos.existe(destino)) {
            return destino;
        }
        if (!reservado) {
            if (e.esCarpeta() && Files.isDirectory(destino, LinkOption.NOFOLLOW_LINKS)) {
                anotar(p, "se junta con la carpeta «" + destino.getFileName() + "» que ya hay allí");
                return destino;
            }
            if (!e.esCarpeta() && p.categoria().getTipo() != Tipo.DUPLICADO
                    && Files.isRegularFile(destino, LinkOption.NOFOLLOW_LINKS)
                    && mismoContenido(e.getRuta(), destino)) {
                Path carpeta = ctx.carpetaDe(reglas.duplicados());
                anotar(p, "ya había una copia idéntica allí: esta va a «Duplicados»");
                return Archivos.nombreLibre(carpeta.resolve(e.getNombre()), ctx::ocupado);
            }
        }
        Path libre = Archivos.nombreLibre(destino, ctx::ocupado);
        anotar(p, "ya hay un «" + destino.getFileName() + "» allí: se guardará como «" + libre.getFileName() + "»");
        return libre;
    }

    private boolean mismoContenido(Path a, Path b) {
        return comparaciones.computeIfAbsent(Archivos.clave(a) + "|" + Archivos.clave(b),
                k -> Archivos.mismoContenido(a, b));
    }

    private static List<Path> listarCarpetas(Path carpeta) {
        if (carpeta == null || !Files.isDirectory(carpeta)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(carpeta)) {
            return s.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Apunta la carpeta de una tarea. Si hay dos para la misma tarea, no se adivina (queda null). */
    private static void registrar(Map<String, Path> mapa, String clave, Path carpeta) {
        if (mapa.containsKey(clave) && !Objects.equals(mapa.get(clave), carpeta)) {
            mapa.put(clave, null);
        } else {
            mapa.put(clave, carpeta);
        }
    }

    /** Datos que se calculan una vez por recálculo (carpetas existentes, nombres ya reservados…). */
    private final class Contexto {
        private final Plan plan;
        private final Map<Categoria, Path> carpetas = new HashMap<>();
        private final Map<Categoria, Map<String, Path>> tareas = new HashMap<>();
        private final Set<String> reservados = new HashSet<>();
        private List<Path> subcarpetasClase;

        Contexto(Plan plan) {
            this.plan = plan;
        }

        Path carpetaDe(Categoria c) {
            Path guardada = carpetas.get(c);
            if (guardada == null && c.getTipo() != Tipo.DEJAR) {
                guardada = calcularCarpeta(c);
                carpetas.put(c, guardada);
            }
            return guardada;
        }

        private Path calcularCarpeta(Categoria c) {
            if (c.getTipo() == Tipo.ASIGNATURA) {
                return carpetaDeAsignatura(c);
            }
            return plan.getDestinoOtros().resolve(Texto.limpiarRuta(c.getCarpeta()));
        }

        /** Usa la carpeta que ya tengas para esa asignatura (por nombre o por siglas) o crea una. */
        private Path carpetaDeAsignatura(Categoria c) {
            Path raiz = plan.getDestinoClase();
            String deseada = Texto.limpiarRuta(c.getCarpeta());
            List<Path> subcarpetas = subcarpetasClase();
            for (Path s : subcarpetas) {
                if (Texto.mismoNombre(s.getFileName().toString(), deseada)) {
                    return s;
                }
            }
            if (!c.isIntegrada()) {
                Path unica = null;
                int cuantas = 0;
                for (Path s : subcarpetas) {
                    if (asignaturaDelNombre(s.getFileName().toString()) == c) {
                        unica = s;
                        cuantas++;
                    }
                }
                if (cuantas == 1) {
                    return unica;
                }
            }
            return raiz.resolve(deseada);
        }

        /** La asignatura a la que más se parece un nombre de carpeta, o null si no está clara. */
        private Categoria asignaturaDelNombre(String nombre) {
            String normalizado = Texto.normalizarNombre(nombre);
            String compacto = normalizado.replace(" ", "");
            Categoria mejor = null;
            int maximo = 0;
            boolean empate = false;
            for (Categoria a : reglas.asignaturas()) {
                int k = a.getClaves().todasEn(normalizado, compacto).size();
                if (k > maximo) {
                    maximo = k;
                    mejor = a;
                    empate = false;
                } else if (k == maximo && k > 0) {
                    empate = true;
                }
            }
            return empate ? null : mejor;
        }

        private List<Path> subcarpetasClase() {
            if (subcarpetasClase == null) {
                subcarpetasClase = listarCarpetas(plan.getDestinoClase());
            }
            return subcarpetasClase;
        }

        /** Carpetas de tareas que ya existen dentro de la asignatura («Tarea 3», «Actividad 2 SI»…). */
        Map<String, Path> tareasExistentes(Categoria c) {
            Map<String, Path> mapa = tareas.get(c);
            if (mapa == null) {
                mapa = new HashMap<>();
                for (Path s : listarCarpetas(carpetaDe(c))) {
                    Optional<DetectorGrupos.Grupo> g = DetectorGrupos.detectar(Texto.normalizarNombre(s.getFileName().toString()));
                    if (g.isPresent()) {
                        registrar(mapa, g.get().clave(), s);
                    }
                }
                tareas.put(c, mapa);
            }
            return mapa;
        }

        boolean reservado(Path p) {
            return reservados.contains(Archivos.clave(p));
        }

        void reservar(Path p) {
            reservados.add(Archivos.clave(p));
        }

        boolean ocupado(Path p) {
            return reservado(p) || Archivos.existe(p);
        }
    }
}
