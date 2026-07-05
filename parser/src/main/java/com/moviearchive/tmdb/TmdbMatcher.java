package com.moviearchive.tmdb;

import com.moviearchive.media.PosterCache;
import com.moviearchive.model.Movie;

import java.util.List;

/**
 * The single place that turns "a parsed title + year" into a fully resolved
 * (or flagged) match. Both the initial scan (ScanPipeline) and the later
 * "re-match now that I have an API key" bulk action (RematchPendingTask)
 * call this, so the matching behavior can never drift between the two.
 */
public class TmdbMatcher {

    public record Outcome(
            Movie.MatchStatus status,
            Integer tmdbId,
            String title,
            String originalTitle,
            Integer releaseYear,
            String overview,
            String overviewFa,
            Double voteAverage,
            Integer voteCount,
            Integer runtimeMinutes,
            String localPosterPath,
            List<String> tags,
            String director,
            List<String> cast,
            String trailerUrl,
            String country,
            String countryFa,
            Double popularity,
            String certification,
            String imdbId,
            String message
    ) {}

    private final TmdbClient client;
    private final PosterCache posterCache; // nullable - poster download skipped if so
    private final MatchEngine matchEngine = new MatchEngine();

    public TmdbMatcher(TmdbClient client, PosterCache posterCache) {
        this.client = client;
        this.posterCache = posterCache;
    }

    public Outcome resolve(String title, Integer year, String alternateTitle) {
        try {
            List<TmdbClient.SearchCandidate> candidates = client.search(title, year);
            if (candidates.isEmpty() && alternateTitle != null) {
                candidates = client.search(alternateTitle, year);
            }

            MatchEngine.Verdict verdict = matchEngine.decide(title, year, candidates);

            return switch (verdict.decision()) {
                case NOT_FOUND -> new Outcome(Movie.MatchStatus.NOT_FOUND,
                        null, null, null, null, null, null, null, null, null, null, List.of(),
                        null, List.of(), null, null, null, null, null, null,
                        "در TMDB پیدا نشد");
                case NEEDS_REVIEW -> new Outcome(Movie.MatchStatus.NEEDS_REVIEW,
                        null, null, null, null, null, null, null, null, null, null, List.of(),
                        null, List.of(), null, null, null, null, null, null,
                        "چند نتیجه‌ی نزدیک پیدا شد - نیاز به بررسی دستی");
                case AUTO_ACCEPT -> fetchFull(verdict.best().tmdbId());
            };
        } catch (Exception e) {
            return new Outcome(Movie.MatchStatus.NOT_FOUND,
                    null, null, null, null, null, null, null, null, null, null, List.of(),
                    null, List.of(), null, null, null, null, null, null,
                    "خطا در ارتباط با TMDB: " + e.getMessage());
        }
    }

    /**
     * Fetches full details for a TMDB id the caller has already confirmed
     * (e.g. the user picked it manually in ReviewDialog) - same fetch logic
     * as the automatic path, so a manual match ends up with exactly the same
     * fields (director, cast, trailer, Persian overview, tags) as an
     * automatic one.
     */
    public Outcome fetchConfirmedMatch(int tmdbId) throws Exception {
        return fetchFull(tmdbId);
    }

    private Outcome fetchFull(int tmdbId) throws Exception {
        TmdbClient.MovieDetails details = client.getDetails(tmdbId);
        List<String> keywords = client.getKeywords(tmdbId);
        TmdbClient.CreditsInfo credits = client.getCredits(tmdbId);
        String certification = client.getCertification(tmdbId);

        // Prefer linking straight to the movie's IMDb page (the person can
        // watch the trailer from there, and it isn't dependent on a YouTube
        // upload being public/available) - falls back to a YouTube search
        // result from TMDB only when no imdb_id exists at all, which is rare.
        String trailerUrl = (details.imdbId() != null && !details.imdbId().isBlank())
                ? "https://www.imdb.com/title/" + details.imdbId() + "/"
                : client.getTrailerUrl(tmdbId);

        Integer releaseYear = null;
        if (details.releaseDate() != null && details.releaseDate().length() >= 4) {
            releaseYear = Integer.parseInt(details.releaseDate().substring(0, 4));
        }

        String localPoster = null;
        if (posterCache != null) {
            String url = client.posterUrl(details.posterPath());
            localPoster = posterCache.fetchAndCache(tmdbId, "poster", url);
        }

        List<String> allTags = new java.util.ArrayList<>(details.genres());
        allTags.addAll(keywords);

        String director = credits.directors().isEmpty() ? null : String.join(", ", credits.directors());

        return new Outcome(Movie.MatchStatus.MATCHED, details.tmdbId(), details.title(),
                details.originalTitle(), releaseYear, details.overview(), details.overviewFa(),
                details.voteAverage(), details.voteCount(), details.runtimeMinutes(), localPoster, allTags,
                director, credits.castDisplayNames(), trailerUrl,
                details.country(), details.countryFa(), details.popularity(), certification, details.imdbId(),
                "تطبیق یافت شد");
    }
}
