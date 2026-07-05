package com.moviearchive.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Properties;

/**
 * Application settings persisted to a local config file, deliberately kept
 * OUTSIDE the SQLite database. Rationale: the database is meant to be
 * exported/shared/backed-up freely (see ArchiveExporter); a TMDB API key
 * must never end up inside a file the user might hand to someone else.
 *
 * Location: %APPDATA%/MovieArchive/config.properties on Windows,
 * falling back to ~/.moviearchive/config.properties elsewhere.
 */
public class AppConfig {

    private static final String TMDB_API_KEY = "tmdb.apiKey";
    private static final String LAST_LIBRARY_PATH = "library.lastPath";
    private static final String DB_PATH = "db.path";
    private static final String PROXY_ENABLED = "proxy.enabled";
    private static final String PROXY_HOST = "proxy.host";
    private static final String PROXY_PORT = "proxy.port";
    private static final String APP_LANGUAGE = "app.language";
    private static final String VIEW_MODE = "ui.viewMode";
    private static final String SORT_MODE = "ui.sortMode";
    private static final String WINDOW_MAXIMIZED = "ui.windowMaximized";
    private static final String WINDOW_WIDTH = "ui.windowWidth";
    private static final String WINDOW_HEIGHT = "ui.windowHeight";

    private final Path configFile;
    private final Properties props = new Properties();

    public AppConfig() {
        this.configFile = resolveConfigFile();
        load();
    }

    private Path resolveConfigFile() {
        String appData = System.getenv("APPDATA");
        Path dir = (appData != null)
                ? Path.of(appData, "MovieArchive")
                : Path.of(System.getProperty("user.home"), ".moviearchive");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
            // fall back to current directory if we truly can't create it
        }
        return dir.resolve("config.properties");
    }

    private void load() {
        if (Files.exists(configFile)) {
            try (InputStream in = Files.newInputStream(configFile)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("Could not read config file: " + e.getMessage());
            }
        }
    }

    public void save() {
        try (OutputStream out = Files.newOutputStream(configFile)) {
            props.store(out, "Movie Archive settings - do NOT share this file (contains API key)");
        } catch (IOException e) {
            System.err.println("Could not write config file: " + e.getMessage());
        }
    }

    public String getTmdbApiKey() {
        return props.getProperty(TMDB_API_KEY, "");
    }

    public void setTmdbApiKey(String key) {
        props.setProperty(TMDB_API_KEY, key == null ? "" : key.trim());
    }

    public boolean hasTmdbApiKey() {
        return !getTmdbApiKey().isBlank();
    }

    public String getLastLibraryPath() {
        return props.getProperty(LAST_LIBRARY_PATH, "");
    }

    public void setLastLibraryPath(String path) {
        props.setProperty(LAST_LIBRARY_PATH, path);
    }

    public Path getDbPath() {
        String custom = props.getProperty(DB_PATH, "");
        if (!custom.isBlank()) return Path.of(custom);
        return configFile.getParent().resolve("archive.db");
    }

    public Path getPosterCacheDir() {
        return configFile.getParent().resolve("posters");
    }

    // --- Proxy settings (needed when TMDB/image.tmdb.org is geo-blocked) ---

    public boolean isProxyEnabled() {
        return Boolean.parseBoolean(props.getProperty(PROXY_ENABLED, "false"));
    }

    public void setProxyEnabled(boolean enabled) {
        props.setProperty(PROXY_ENABLED, Boolean.toString(enabled));
    }

    public String getProxyHost() {
        return props.getProperty(PROXY_HOST, "127.0.0.1");
    }

    public void setProxyHost(String host) {
        props.setProperty(PROXY_HOST, host == null ? "" : host.trim());
    }

    public int getProxyPort() {
        try {
            return Integer.parseInt(props.getProperty(PROXY_PORT, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void setProxyPort(int port) {
        props.setProperty(PROXY_PORT, Integer.toString(port));
    }

    /**
     * Builds a ProxySelector for HttpClient based on current settings, or
     * null if no proxy should be used. Note: java.net.http.HttpClient only
     * reliably supports HTTP-type proxies (with CONNECT tunneling for
     * https://); it does NOT support SOCKS proxies. Point this at the HTTP
     * proxy port of your VPN/circumvention tool, not its SOCKS5 port.
     */
    public java.net.ProxySelector toProxySelector() {
        if (!isProxyEnabled() || getProxyHost().isBlank() || getProxyPort() <= 0) {
            return null;
        }
        return java.net.ProxySelector.of(new java.net.InetSocketAddress(getProxyHost(), getProxyPort()));
    }

    // --- App language ("fa" or "en") ---

    public String getAppLanguage() {
        return props.getProperty(APP_LANGUAGE, "fa");
    }

    public void setAppLanguage(String language) {
        props.setProperty(APP_LANGUAGE, language);
    }

    // --- Remembered UI state (so the app looks the same next time it opens) ---

    public String getViewMode() {
        return props.getProperty(VIEW_MODE, "");
    }

    public void setViewMode(String viewMode) {
        props.setProperty(VIEW_MODE, viewMode == null ? "" : viewMode);
    }

    public String getSortMode() {
        return props.getProperty(SORT_MODE, "");
    }

    public void setSortMode(String sortMode) {
        props.setProperty(SORT_MODE, sortMode == null ? "" : sortMode);
    }

    public boolean isWindowMaximized() {
        return Boolean.parseBoolean(props.getProperty(WINDOW_MAXIMIZED, "false"));
    }

    public void setWindowMaximized(boolean maximized) {
        props.setProperty(WINDOW_MAXIMIZED, Boolean.toString(maximized));
    }

    public double getWindowWidth() {
        try {
            return Double.parseDouble(props.getProperty(WINDOW_WIDTH, "1600"));
        } catch (NumberFormatException e) {
            return 1600;
        }
    }

    public void setWindowWidth(double width) {
        props.setProperty(WINDOW_WIDTH, Double.toString(width));
    }

    public double getWindowHeight() {
        try {
            return Double.parseDouble(props.getProperty(WINDOW_HEIGHT, "860"));
        } catch (NumberFormatException e) {
            return 860;
        }
    }

    public void setWindowHeight(double height) {
        props.setProperty(WINDOW_HEIGHT, Double.toString(height));
    }
}
