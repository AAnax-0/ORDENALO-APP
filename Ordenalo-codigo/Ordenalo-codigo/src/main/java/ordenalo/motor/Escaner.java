package ordenalo.motor;

import ordenalo.modelo.Elemento;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Lista lo que hay suelto en una carpeta (sin entrar en subcarpetas: una carpeta de una
 * actividad se mueve entera) y resume qué hay dentro de cada carpeta para poder clasificarla.
 */
public final class Escaner {

    /** Resultado: los elementos y cuántas cosas se han saltado a propósito. */
    public record Resultado(List<Elemento> elementos, int ocultos, int enDescarga, int excluidos) {
        public int ignorados() {
            return ocultos + enDescarga;
        }
    }

    private static final Set<String> IGNORAR = Set.of("desktop.ini", "thumbs.db", ".ds_store", "$recycle.bin",
            "system volume information", "icon\r", ".localized");
    private static final Set<String> EN_DESCARGA = Set.of("crdownload", "part", "partial", "download", "opdownload",
            "tmp", "temp");
    /** Carpetas de herramientas que no dicen nada de qué es el proyecto (además de todas las que empiezan por punto). */
    private static final Set<String> NO_ENTRAR = Set.of("node_modules", "target", "build", "out", "bin", "obj",
            "venv", "__pycache__", "dist", "nbproject");
    /** Archivos de configuración de proyectos: su extensión no cuenta (el build.xml de NetBeans no es de Lenguajes de Marcas). */
    private static final Set<String> NO_CONTAR = Set.of("build.xml", "pom.xml", "manifest.mf", "androidmanifest.xml",
            "settings.gradle", "gradle.properties", "gradlew", "gradlew.bat", "package.json", "package-lock.json",
            "tsconfig.json", "desktop.ini", "thumbs.db");

    private static final int PROFUNDIDAD_MAXIMA = 8;
    private static final int MAX_ARCHIVOS = 5000;
    private static final int MAX_NOMBRES = 150;

    private Escaner() {
    }

    /**
     * @param origen  carpeta a ordenar
     * @param excluir rutas que no se pueden tocar (los destinos, la propia aplicación…):
     *                se salta cualquier elemento que sea una de ellas o las contenga
     * @param aviso   recibe mensajes de progreso («Mirando dentro de…»)
     */
    public static Resultado escanear(Path origen, Collection<Path> excluir, Consumer<String> aviso) throws IOException {
        List<Path> hijos;
        try (Stream<Path> s = Files.list(origen)) {
            hijos = s.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT))).toList();
        }
        List<Path> excluidas = excluir.stream().filter(Objects::nonNull)
                .map(p -> p.toAbsolutePath().normalize()).toList();

        List<Elemento> elementos = new ArrayList<>();
        int ocultos = 0;
        int enDescarga = 0;
        int excluidos = 0;
        for (Path p : hijos) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            String nombre = p.getFileName().toString();
            String minusculas = nombre.toLowerCase(Locale.ROOT);
            if (IGNORAR.contains(minusculas) || nombre.startsWith(".") || nombre.startsWith("~$")
                    || minusculas.startsWith(".~lock")) {
                ocultos++;
                continue;
            }
            BasicFileAttributes atributos;
            try {
                atributos = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            } catch (IOException ex) {
                continue;
            }
            if (esIntocable(p, atributos)) {
                ocultos++;
                continue;
            }
            Path absoluta = p.toAbsolutePath().normalize();
            if (excluidas.stream().anyMatch(x -> x.startsWith(absoluta))) {
                excluidos++;
                continue;
            }
            if (atributos.isDirectory()) {
                aviso.accept("Mirando dentro de «" + nombre + "»…");
                elementos.add(analizarCarpeta(p, atributos));
            } else {
                if (EN_DESCARGA.contains(Texto.extension(nombre))) {
                    enDescarga++;
                    continue;
                }
                elementos.add(Elemento.archivo(p, atributos.size(), atributos.lastModifiedTime().toMillis()));
            }
        }
        return new Resultado(elementos, ocultos, enDescarga, excluidos);
    }

    /**
     * Lo que nunca se toca: enlaces simbólicos, uniones de Windows (como «Mi música» dentro de Documentos)
     * y todo lo que esté marcado como oculto o de sistema.
     * <p>
     * Ojo: {@code Files.isHidden} sigue los enlaces, así que no sirve para esto; se leen los atributos
     * del propio elemento, sin seguirlo.
     */
    public static boolean esIntocable(Path p, BasicFileAttributes atributos) {
        if (atributos.isSymbolicLink() || atributos.isOther()) {
            return true;
        }
        try {
            DosFileAttributes dos = Files.readAttributes(p, DosFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return dos.isHidden() || dos.isSystem();
        } catch (IOException | UnsupportedOperationException e) {
            return false; // Linux o Mac sin atributos de Windows: basta con lo anterior
        }
    }

    /** Igual que {@link #esIntocable(Path, BasicFileAttributes)}, leyendo antes los atributos. */
    public static boolean esIntocable(Path p) {
        try {
            return esIntocable(p, Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS));
        } catch (IOException e) {
            return false;
        }
    }

    private static Elemento analizarCarpeta(Path carpeta, BasicFileAttributes atributos) {
        Map<String, Integer> extensiones = new HashMap<>();
        List<String> nombres = new ArrayList<>();
        Set<String> marcas = new HashSet<>();
        int[] archivos = {0};
        long[] bytes = {0};
        boolean[] incompleto = {false};
        try {
            Files.walkFileTree(carpeta, EnumSet.noneOf(FileVisitOption.class), PROFUNDIDAD_MAXIMA, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) {
                    if (dir.equals(carpeta)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String nombre = dir.getFileName().toString();
                    String minusculas = nombre.toLowerCase(Locale.ROOT);
                    int profundidad = carpeta.relativize(dir).getNameCount();
                    if (profundidad == 1) {
                        anotarMarca(minusculas, marcas);
                    }
                    if (nombre.startsWith(".") || NO_ENTRAR.contains(minusculas) || esIntocable(dir, a)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (profundidad <= 2 && nombres.size() < MAX_NOMBRES) {
                        nombres.add(Texto.normalizarNombre(nombre));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path archivo, BasicFileAttributes a) {
                    String nombre = archivo.getFileName().toString();
                    String minusculas = nombre.toLowerCase(Locale.ROOT);
                    int profundidad = carpeta.relativize(archivo).getNameCount();
                    if (profundidad == 1) {
                        anotarMarca(minusculas, marcas);
                    }
                    if (a.isDirectory() || nombre.startsWith(".") || NO_CONTAR.contains(minusculas)
                            || a.isSymbolicLink() || a.isOther()) {
                        return FileVisitResult.CONTINUE; // carpetas al límite, ocultos, configuración y enlaces
                    }
                    archivos[0]++;
                    bytes[0] += a.size();
                    String ext = Texto.extension(nombre);
                    if (!ext.isEmpty()) {
                        extensiones.merge(ext, 1, Integer::sum);
                    }
                    if (profundidad <= 2 && nombres.size() < MAX_NOMBRES) {
                        nombres.add(Texto.normalizarNombre(Texto.raiz(nombre)));
                    }
                    if (archivos[0] >= MAX_ARCHIVOS) {
                        incompleto[0] = true;
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path archivo, IOException ex) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            incompleto[0] = true;
        }
        return new Elemento(carpeta, carpeta.getFileName().toString(), true, bytes[0],
                atributos.lastModifiedTime().toMillis(), extensiones, archivos[0], nombres, incompleto[0], marcas);
    }

    /** Pistas sobre qué es la carpeta según lo que tiene justo dentro. */
    private static void anotarMarca(String nombre, Set<String> marcas) {
        switch (nombre) {
            case ".git" -> marcas.add(Elemento.MARCA_GIT);
            case ".obsidian" -> marcas.add(Elemento.MARCA_OBSIDIAN);
            case "ordenalo.jar" -> marcas.add(Elemento.MARCA_ORDENALO);
            default -> { /* nada */ }
        }
    }
}
