#!/bin/sh
# Ordénalo: abre la aplicación en Linux o Mac. Necesita Java 17 o superior.
cd "$(dirname "$0")" || exit 1

# Para que los nombres con tildes y eñes funcionen aunque el sistema no tenga idioma configurado
case "${LC_ALL:-${LC_CTYPE:-$LANG}}" in
  *UTF-8*|*utf8*|*UTF8*|*utf-8*) ;;
  *) export LC_ALL=C.UTF-8 ;;
esac

JAVA=""
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA="java"
else
  for d in "$HOME"/.jdks/* "$HOME"/.local/share/JetBrains/Toolbox/apps/*/jbr /opt/idea*/jbr /snap/intellij-idea-*/current/jbr /usr/lib/jvm/*; do
    if [ -x "$d/bin/java" ]; then JAVA="$d/bin/java"; break; fi
  done
fi

if [ -z "$JAVA" ]; then
  echo "No encuentro Java. Instala Java 17 o superior, por ejemplo: sudo apt install openjdk-21-jre"
  exit 1
fi
exec "$JAVA" -jar Ordenalo.jar "$@"
