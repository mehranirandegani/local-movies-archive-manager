@echo off
REM Builds MovieArchive into a standalone Windows package that bundles its
REM own Java runtime - end users do NOT need Java installed.
REM
REM Prerequisites (only on the machine that BUILDS the package, not on the
REM machines that will run it):
REM   1. JDK 17+ (jpackage is included since JDK 14)
REM   2. Maven
REM   3. WiX Toolset (https://wixtoolset.org/) - ONLY needed if PKG_TYPE
REM      below is msi or exe. Not needed for app-image.
REM
REM PKG_TYPE options:
REM   app-image  -> a folder with MovieArchive.exe + bundled runtime inside.
REM                 No WiX needed. Zip the folder and share it, or make a
REM                 desktop shortcut to the .exe inside it. Most reliable.
REM   msi        -> a proper Windows Installer package. Needs WiX, but uses
REM                 WiX's simpler (non-Bundle) toolchain - usually works
REM                 across WiX versions without issues.
REM   exe        -> a single self-extracting installer .exe. Needs WiX's
REM                 "Bundle/Burn" toolchain specifically, which has known
REM                 version-compatibility quirks (e.g. some WiX 3.14 builds
REM                 fail here). If this fails for you, use msi or app-image
REM                 instead rather than troubleshooting WiX versions.

REM IMPORTANT: do NOT add --module-path pointing at a separately-downloaded
REM JavaFX SDK, and do NOT list javafx.controls/javafx.fxml in --add-modules.
REM JavaFX is already fully bundled as plain classpath classes inside the
REM shaded jar (via the "win" classifier Maven dependencies in pom.xml).
REM Mixing that with a real modular JavaFX SDK causes a duplicate/conflicting
REM copy of JavaFX classes, which is what "Failed to launch JVM" at runtime
REM usually means in this setup.

setlocal

set PKG_TYPE=app-image

echo === Step 1/2: Building the application jar (with all dependencies bundled) ===
call mvn -f parser\pom.xml clean package
if errorlevel 1 (
    echo Maven build failed - fix the error above before continuing.
    exit /b 1
)

echo.
echo === Step 2/2: Creating the package (type: %PKG_TYPE%) ===

set APP_VERSION=1.0.0
set MAIN_JAR=movie-archive-0.1.0.jar

REM --win-menu / --win-shortcut only apply to installer types (msi/exe),
REM not app-image (which is just a plain folder - nothing to register).
set EXTRA_ARGS=
if not "%PKG_TYPE%"=="app-image" (
    set EXTRA_ARGS=--win-menu --win-shortcut
)

REM --win-console: TEMPORARY debugging aid. Without it, the generated .exe
REM is a GUI-subsystem launcher with no console at all, so if the app fails
REM on startup you see nothing - not even in cmd. With it, a console window
REM opens showing the real Java exception/stack trace. Remove this line
REM once the app is confirmed working, so end users don't get a console window.
set EXTRA_ARGS=%EXTRA_ARGS% --win-console

jpackage ^
  --type %PKG_TYPE% ^
  --name "MovieArchive" ^
  --input parser\target ^
  --main-jar %MAIN_JAR% ^
  --main-class com.moviearchive.ui.Launcher ^
  --add-modules java.desktop,java.sql,java.net.http,java.logging,jdk.unsupported ^
  %EXTRA_ARGS% ^
  --app-version %APP_VERSION% ^
  --vendor "MovieArchive" ^
  --description "Personal offline movie archive" ^
  --dest dist

if errorlevel 1 (
    echo.
    echo jpackage failed with type "%PKG_TYPE%".
    echo  - If PKG_TYPE was exe or msi: this is very likely a WiX Toolset
    echo    version/extension issue, not a problem with the app itself.
    echo    Edit this file, set PKG_TYPE=app-image, and run again - that
    echo    mode never needs WiX and always works if the jar itself is fine.
    exit /b 1
)

echo.
echo Done. Output is in the "dist" folder.
if "%PKG_TYPE%"=="app-image" (
    echo Run dist\MovieArchive\MovieArchive.exe directly, or zip the whole
    echo "dist\MovieArchive" folder to share it with others.
    echo.
    echo If double-clicking MovieArchive.exe shows "Failed to launch JVM",
    echo that almost always means --module-path was added back in pointing
    echo at a separate JavaFX SDK - remove it and rebuild. JavaFX does not
    echo need to be a module here; it's already in the jar.
)
endlocal