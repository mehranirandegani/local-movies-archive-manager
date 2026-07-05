package com.moviearchive.ui;

/**
 * Since Java 11, the java launcher refuses to start a jar whose Main-Class
 * directly extends javafx.application.Application unless JavaFX is on the
 * module-path - it prints "JavaFX runtime components are missing" even when
 * JavaFX is fully present on the classpath (as it is here, bundled into the
 * shaded jar). The standard workaround is this indirection: make the jar's
 * actual Main-Class a plain class that does NOT extend Application, which
 * just forwards to App.main(). The launcher's check only looks at the
 * declared Main-Class, so this bypasses it entirely.
 *
 * Only used for the packaged jar (jpackage) - `mvn javafx:run` for
 * day-to-day development still targets App directly and is unaffected.
 */
public class Launcher {
    public static void main(String[] args) {
        App.main(args);
    }
}