# Ordénalo

Aplicación de escritorio en **Java 17 + Swing** (con el aspecto moderno de [FlatLaf](https://www.formdev.com/flatlaf/))
que ordena el Escritorio, Descargas o cualquier carpeta: reconoce de qué **asignatura** y de qué **tarea** es cada cosa,
enseña la propuesta antes de mover nada y permite **deshacerlo todo**.

```
Escritorio/                              Documentos/DAM/
  Actividad 2 SI/              ──►         Sistemas Informáticos/
  actividad2 SI enlaces.txt                  Actividad 2 SI/   (con el .txt dentro)
  tarea3-factura/  (IntelliJ)              Programación/
  Ejercicio1.java                            tarea3-factura/
  SQLite trabajo.docx                        Ejercicios/Ejercicio1.java
  Captura de pantalla … .png               Bases de Datos/SQLite trabajo.docx
  jdk-21_windows-x64_bin.exe             Documentos/Ordenado/
  normas.pdf + normas (1).pdf                Capturas de pantalla/ · Instaladores/ · Duplicados/
```

## Ejecutarlo desde IntelliJ IDEA

1. **File → Open…** y elige esta carpeta (la que tiene `pom.xml`).
2. Espera a que Maven descargue las dependencias (FlatLaf y JUnit).
3. Abre `src/main/java/ordenalo/App.java` y ejecuta `main`.

| Para… | Haz |
|---|---|
| Generar el `.jar` | Pestaña Maven → Lifecycle → `package` (o `mvn package`). Sale en `target/Ordenalo.jar` |
| Pasar los tests | `mvn test` · 36 pruebas, una de ellas ordena un escritorio de prueba y comprueba que deshacer lo deja idéntico |
| Probar sin ventana | `java -jar target/Ordenalo.jar --simular <carpeta>` |

Los lanzadores para Windows y Linux y el manual de uso están en `lanzadores/`.

## Cómo está hecho

```
src/main/java/ordenalo/
├── App.java              entrada: abre la ventana o atiende la línea de comandos
├── Arranque.java         compilada para Java 8: si el PC tiene un Java viejo, lo explica
├── Cli.java              --simular · --aplicar · --deshacer · --historial
├── modelo/               datos puros: Categoria, Elemento, Propuesta, Plan, Clasificacion…
├── motor/                toda la lógica, sin nada de interfaz (por eso se puede probar con JUnit)
│   ├── Texto             normaliza nombres: «Tarea3_SI-final» → « tarea 3 si final »
│   ├── PalabraClave      busca palabras completas, con plural, y las largas también pegadas
│   ├── Clasificador      decide la categoría por puntos
│   ├── DetectorGrupos    reconoce la tarea: «Tarea 3», «UD2 act1», «ejercicio5»…
│   ├── Escaner           lista lo suelto y resume qué hay dentro de cada carpeta
│   ├── Planificador      destinos, carpetas que ya tienes, sueltos con su tarea, duplicados, choques
│   ├── Ejecutor          mueve (también a otro disco o USB) y deshace
│   ├── Historial         registro de cada ordenación, escrito línea a línea
│   └── Reglas · Config · CarpetasSistema · Archivos
└── ui/                   ventana Swing: pestañas Ordenar, Reglas e Historial
```

### Cómo decide a dónde va cada cosa

1. Accesos directos, ocultos, descargas a medias y carpetas de programas **no se tocan**.
2. Instaladores e ISOs van por tipo aunque el nombre diga otra cosa.
3. Cada asignatura suma puntos: **10** por palabra clave en el nombre, **6** por la extensión,
   **4–8** por los tipos de archivo de dentro (si es carpeta) y **3** por palabras clave en los nombres de dentro.
4. Gana la que más puntos tiene. Si la segunda está muy cerca, la propuesta sale como **dudosa** y no se mueve sola.
5. Si parece de clase («Tarea 4») pero no se sabe de qué asignatura, va a «Sin asignatura» como dudosa.
6. Si no es de clase, va por tipo de archivo (imágenes, PDF, comprimidos…).

Las reglas son un archivo de texto (`reglas.ini`) que se edita desde la pestaña **Reglas** o a mano.

### Cómo deshace

Antes de mover nada se abre un registro en `datos/historial/AAAAMMDD-HHMMSS.txt`, y cada paso se escribe y se guarda
en el momento (`MOVIDO`, `CREADA`, `BORRADA`). Deshacer recorre la lista al revés: devuelve cada cosa a su sitio y quita
las carpetas que se crearon si han quedado vacías. Como se escribe paso a paso, aunque el programa se cierre a medias
se puede deshacer lo que llegó a hacer.

### Reglas de seguridad

- Nunca se borra nada: las copias repetidas van a `Duplicados`.
- Nunca se sobrescribe: si un nombre choca se guarda como `nombre (2)`; si es una carpeta, se juntan.
- Las carpetas se mueven enteras (un proyecto de IntelliJ sigue funcionando).
- No deja ordenar la raíz de un disco, la carpeta de usuario entera ni carpetas del sistema.
