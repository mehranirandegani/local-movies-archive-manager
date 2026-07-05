package com.moviearchive.media;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Downloads a poster/backdrop image exactly once and stores it under the
 * local cache folder. Subsequent loads (grid scrolling, re-opening the app)
 * read straight from disk - this is what makes the archive fully usable
 * offline after the first fetch.
 */
public class PosterCache {

    private final Path cacheDir;
    private final HttpClient http;

    public PosterCache(Path cacheDir) throws IOException {
        this(cacheDir, null);
    }

    public PosterCache(Path cacheDir, java.net.ProxySelector proxySelector) throws IOException {
        this.cacheDir = cacheDir;
        Files.createDirectories(cacheDir);
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10));
        if (proxySelector != null) {
            builder.proxy(proxySelector);
        }
        this.http = builder.build();
    }

    /**
     * Downloads the image at remoteUrl if not already cached for this
     * tmdbId + kind, and returns the local absolute path (as a string,
     * ready to store in the movies table / hand to a JavaFX Image).
     */
    public String fetchAndCache(int tmdbId, String kind, String remoteUrl) throws IOException, InterruptedException {
        if (remoteUrl == null) return null;

        String ext = remoteUrl.substring(remoteUrl.lastIndexOf('.'));
        Path target = cacheDir.resolve(tmdbId + "_" + kind + ext);

        if (Files.exists(target)) {
            return target.toAbsolutePath().toString(); // cache hit - no network call
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(remoteUrl))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to download image: HTTP " + response.statusCode());
        }
        Files.write(target, response.body());
        return target.toAbsolutePath().toString();
    }
}
