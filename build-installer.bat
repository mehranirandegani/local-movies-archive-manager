@echo off
REM Builds MovieArchive into a native Windows installer (.exe) that bundles
REM its own Java runtime - end users do NOT need Java installed.
REM
REM Prerequisites (only on the machine that BUILDS the installer, not on
REM the machines that will run it):
REM   1. JDK 17+ (jpackage is included since JDK 14)
REM   2. Maven
REM   3. WiX Toolset v3 (https://wixtoolset.org/) - required by jpackage
REM      for --type exe/msi on Windows. If you don't want to install WiX,
REM      change --type exe to --type app-image below (produces a folder
REM      you can zip and share instead of a single installer file).

setlocal

echo === Step 1/2: Building the application jar (with all dependencies bundled) ===
call mvn -f parser\pom.xml clean package
if errorlevel 1 (
    echo Maven build failed - fix the error above before continuing.
    exit /b 1
)

echo.
echo === Step 2/2: Creating the native installer with jpackage ===

set APP_VERSION=1.0.0
set MAIN_JAR=movie-archive-0.1.0.jar

jpackage ^
  --type app-image ^
  --name "MovieArchive" ^
  --input parser\target ^
  --main-jar %MAIN_JAR% ^
  --main-class com.moviearchive.ui.App ^
  --module-path "C:\javafx-sdk-21.0.11\lib" ^
  --add-modules javafx.controls,javafx.fxml,java.desktop,java.sql,java.net.http,java.logging ^
  --app-version %APP_VERSION% ^
  --vendor "MovieArchive" ^
  --description "Personal offline movie archive" ^
  --dest dist

if errorlevel 1 (
    echo.
    echo jpackage failed. Common causes:
    echo  - WiX Toolset not installed (needed for --type exe/msi^) - try --type app-image instead
    echo  - jpackage not found - make sure your JDK's bin folder is on PATH
    exit /b 1
)

echo.
echo Done. Installer is in the "dist" folder.
endlocal
