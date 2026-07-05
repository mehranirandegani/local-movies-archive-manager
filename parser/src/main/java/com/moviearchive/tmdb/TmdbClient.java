package com.moviearchive.tmdb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin client around the TMDB v3 API (https://developer.themoviedb.org/reference).
 * Requires a free API key: https://www.themoviedb.org/settings/api
 *
 * Only the endpoints we need for the archive are implemented:
 *  - search/movie      -> find candidates by title (+optional year)
 *  - movie/{id}         -> full details (overview, runtime, vote_average, genres)
 *  - movie/{id}/keywords -> content tags
 *  - image base URL      -> to download posters
 */
public class TmdbClient {

    private static final String BASE_URL = "https://api.themoviedb.org/3";
    // Image base is versioned via /configuration in production; hardcoding the
    // common default here to keep this client dependency-free of extra calls.
    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";

    private final String apiKey;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public TmdbClient(String apiKey) {
        this(apiKey, null);
    }

    /**
     * @param proxySelector optional - use when TMDB is geo-blocked from your
     *                       network (e.g. sanctions-based blocking). Must
     *                       point at an HTTP proxy port, not a SOCKS5 port -
     *                       java.net.http.HttpClient does not support SOCKS.
     */
    public TmdbClient(String apiKey, java.net.ProxySelector proxySelector) {
        this.apiKey = apiKey;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10));
        if (proxySelector != null) {
            builder.proxy(proxySelector);
        }
        this.http = builder.build();
    }

    public record SearchCandidate(
            int tmdbId,
            String title,
            String originalTitle,
            String releaseDate, // yyyy-MM-dd
            double popularity,
            String posterPath
    ) {}

    public record MovieDetails(
            int tmdbId,
            String title,
            String originalTitle,
            String overview,
            String overviewFa,
            String releaseDate,
            double voteAverage,
            int voteCount,
            int runtimeMinutes,
            String posterPath,
            String backdropPath,
            List<String> genres,
            String country,
            String countryFa,
            double popularity,
            String imdbId
    ) {}

    /**
     * Searches for candidate movies by title, optionally narrowed by year.
     * Returns results ordered by TMDB's relevance/popularity ranking.
     */
    public List<SearchCandidate> search(String title, Integer year) throws IOException, InterruptedException {
        StringBuilder url = new StringBuilder(BASE_URL)
                .append("/search/movie?query=")
                .append(URLEncoder.encode(title, StandardCharsets.UTF_8))
                .append("&include_adult=false&language=en-US");
        if (year != null) {
            url.append("&year=").append(year);
        }

        JsonNode root = getJson(url.toString());
        List<SearchCandidate> results = new ArrayList<>();
        for (JsonNode item : root.path("results")) {
            results.add(new SearchCandidate(
                    item.path("id").asInt(),
                    item.path("title").asText(null),
                    item.path("original_title").asText(null),
                    item.path("release_date").asText(null),
                    item.path("popularity").asDouble(0),
                    item.path("poster_path").asText(null)
            ));
        }
        return results;
    }

    /**
     * Fetches full details + genres for a specific TMDB movie id. Title,
     * genres etc. come from the English response. Overview and country are
     * fetched in both English and Persian (when TMDB has a Persian
     * translation) and kept as separate fields, so the app can display
     * whichever one matches its current language setting without needing
     * to re-fetch when the user switches languages.
     */
    public MovieDetails getDetails(int tmdbId) throws IOException, InterruptedException {
        String url = BASE_URL + "/movie/" + tmdbId + "?language=en-US";
        JsonNode root = getJson(url);

        List<String> genres = new ArrayList<>();
        for (JsonNode g : root.path("genres")) {
            genres.add(g.path("name").asText());
        }

        String overview = root.path("overview").asText(null);

        List<String> countryNames = new ArrayList<>();
        for (JsonNode c : root.path("production_countries")) {
            String name = c.path("name").asText(null);
            if (name != null) countryNames.add(name);
        }
        String country = countryNames.isEmpty() ? null : String.join(", ", countryNames);

        PersianExtras fa = tryFetchPersianExtras(tmdbId);

        return new MovieDetails(
                root.path("id").asInt(),
                root.path("title").asText(null),
                root.path("original_title").asText(null),
                overview,
                fa.overview(),
                root.path("release_date").asText(null),
                root.path("vote_average").asDouble(0),
                root.path("vote_count").asInt(0),
                root.path("runtime").asInt(0),
                root.path("poster_path").asText(null),
                root.path("backdrop_path").asText(null),
                genres,
                country,
                fa.country(),
                root.path("popularity").asDouble(0),
                root.path("imdb_id").asText(null)
        );
    }

    private record PersianExtras(String overview, String country) {}

    /**
     * One extra request (language=fa-IR) that grabs both the Persian
     * overview and Persian country names together, since they're both in
     * the same response. Returns nulls (never throws) if TMDB has no
     * Persian translation for this movie yet or the request fails.
     */
    private PersianExtras tryFetchPersianExtras(int tmdbId) {
        try {
            JsonNode root = getJson(BASE_URL + "/movie/" + tmdbId + "?language=fa-IR");
            String overview = root.path("overview").asText(null);
            if (overview != null && overview.isBlank()) overview = null;

            List<String> countryNames = new ArrayList<>();
            for (JsonNode c : root.path("production_countries")) {
                String name = c.path("name").asText(null);
                if (name != null) countryNames.add(name);
            }
            String country = countryNames.isEmpty() ? null : String.join("، ", countryNames);

            return new PersianExtras(overview, country);
        } catch (Exception e) {
            return new PersianExtras(null, null);
        }
    }

    public record CreditsInfo(List<String> castDisplayNames, List<String> directors) {}

    /**
     * Fetches the top ~10 billed cast members (formatted as "Name (Character)")
     * and any credited directors. Returns an empty CreditsInfo (never null,
     * never throws) if the request fails - a missing cast list shouldn't
     * fail the whole match.
     */
    public CreditsInfo getCredits(int tmdbId) {
        try {
            JsonNode root = getJson(BASE_URL + "/movie/" + tmdbId + "/credits?language=en-US");

            List<String> cast = new ArrayList<>();
            int count = 0;
            for (JsonNode c : root.path("cast")) {
                if (count++ >= 10) break;
                String name = c.path("name").asText(null);
                if (name == null) continue;
                String character = c.path("character").asText(null);
                cast.add((character != null && !character.isBlank()) ? name + " (" + character + ")" : name);
            }

            List<String> directors = new ArrayList<>();
            for (JsonNode c : root.path("crew")) {
                if ("Director".equals(c.path("job").asText())) {
                    String name = c.path("name").asText(null);
                    if (name != null) directors.add(name);
                }
            }
            return new CreditsInfo(cast, directors);
        } catch (Exception e) {
            return new CreditsInfo(List.of(), List.of());
        }
    }

    /**
     * Finds a YouTube trailer URL for the movie, preferring official trailers.
     * Returns null (never throws) if none is found or the request fails.
     * Used as a fallback when no IMDb id is available - see TmdbMatcher,
     * which prefers linking straight to the movie's IMDb page (its trailer
     * is featured there and IMDb tends to be reachable even when YouTube
     * trailers are geo-blocked, private, or removed).
     */
    public String getTrailerUrl(int tmdbId) {
        try {
            JsonNode root = getJson(BASE_URL + "/movie/" + tmdbId + "/videos?language=en-US");
            String fallback = null;
            for (JsonNode v : root.path("results")) {
                if (!"YouTube".equals(v.path("site").asText())) continue;
                if (!"Trailer".equals(v.path("type").asText())) continue;
                String key = v.path("key").asText(null);
                if (key == null || key.isBlank()) continue;
                String url = "https://www.youtube.com/watch?v=" + key;
                if (v.path("official").asBoolean(false)) return url;
                if (fallback == null) fallback = url;
            }
            return fallback;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fetches the US theatrical certification (e.g. "PG-13") when available,
     * falling back to the first non-empty certification found for any
     * country. Returns null (never throws) if nothing is found.
     */
    public String getCertification(int tmdbId) {
        try {
            JsonNode root = getJson(BASE_URL + "/movie/" + tmdbId + "/release_dates");
            String fallback = null;
            for (JsonNode countryNode : root.path("results")) {
                String iso = countryNode.path("iso_3166_1").asText("");
                for (JsonNode rd : countryNode.path("release_dates")) {
                    String cert = rd.path("certification").asText(null);
                    if (cert == null || cert.isBlank()) continue;
                    if ("US".equals(iso)) return cert;
                    if (fallback == null) fallback = cert;
                }
            }
            return fallback;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fetches content keywords for a movie - these double as free-form tags
     * (e.g. "based on novel", "time travel", "post-apocalyptic").
     */
    public List<String> getKeywords(int tmdbId) throws IOException, InterruptedException {
        String url = BASE_URL + "/movie/" + tmdbId + "/keywords";
        JsonNode root = getJson(url);
        List<String> keywords = new ArrayList<>();
        for (JsonNode k : root.path("keywords")) {
            keywords.add(k.path("name").asText());
        }
        return keywords;
    }

    public String posterUrl(String posterPath) {
        if (posterPath == null) return null;
        return IMAGE_BASE_URL + posterPath;
    }

    private JsonNode getJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 429) {
            throw new IOException("TMDB rate limit hit (HTTP 429). Slow down request rate.");
        }
        if (response.statusCode() != 200) {
            throw new IOException("TMDB request failed: HTTP " + response.statusCode() + " - " + response.body());
        }
        return mapper.readTree(response.body());
    }
}
