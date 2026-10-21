@echo off
rem ============================================================
rem  Ordenalo - abre la aplicacion con el Java de este ordenador
rem  (busca el de IntelliJ, Amazon Corretto, Adoptium, Oracle...)
rem ============================================================
setlocal EnableExtensions
set "APP=%~dp0Ordenalo.jar"
set "JAVAW="

if not exist "%APP%" (
  echo.
  echo  No encuentro Ordenalo.jar junto a este archivo.
  echo  Descomprime la carpeta Ordenalo entera y vuelve a probar.
  echo.
  pause
  exit /b 1
)

rem 1) JAVA_HOME
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVAW=%JAVA_HOME%\bin\javaw.exe"

rem 2) Java descargado desde IntelliJ (carpeta .jdks)
if not defined JAVAW for /d %%D in ("%USERPROFILE%\.jdks\*") do if not defined JAVAW if exist "%%D\bin\javaw.exe" set "JAVAW=%%D\bin\javaw.exe"

rem 3) Java que trae IntelliJ IDEA
if not defined JAVAW for /d %%D in ("%ProgramFiles%\JetBrains\*") do if not defined JAVAW if exist "%%D\jbr\bin\javaw.exe" set "JAVAW=%%D\jbr\bin\javaw.exe"
if not defined JAVAW for /d %%D in ("%LOCALAPPDATA%\Programs\*") do if not defined JAVAW if exist "%%D\jbr\bin\javaw.exe" set "JAVAW=%%D\jbr\bin\javaw.exe"

rem 4) Java instalado en el ordenador
if not defined JAVAW for /d %%D in ("%ProgramFiles%\Amazon Corretto\*" "%ProgramFiles%\Eclipse Adoptium\*" "%ProgramFiles%\Microsoft\jdk*" "%ProgramFiles%\Zulu\*" "%ProgramFiles%\BellSoft\*" "%ProgramFiles%\Java\jdk*") do if not defined JAVAW if exist "%%D\bin\javaw.exe" set "JAVAW=%%D\bin\javaw.exe"

rem 5) El que este en el PATH
if not defined JAVAW for %%J in (javaw.exe) do if not "%%~$PATH:J"=="" set "JAVAW=%%~$PATH:J"

if not defined JAVAW goto sinjava
start "" "%JAVAW%" -jar "%APP%" %*
exit /b 0

:sinjava
echo.
echo  No he encontrado Java en este ordenador.
echo  Ordenalo necesita Java 17 o superior (si tienes IntelliJ IDEA, ya lo tienes).
echo  Puedes instalarlo gratis desde https://adoptium.net
echo.
pause
exit /b 1
