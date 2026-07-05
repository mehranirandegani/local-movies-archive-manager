package com.moviearchive.ui;

import com.moviearchive.db.MovieRepository;
import com.moviearchive.media.PosterCache;
import com.moviearchive.model.Movie;
import com.moviearchive.parser.MovieNameParser;
import com.moviearchive.scan.ArchiveScanner;
import com.moviearchive.tmdb.ConcurrentMatchRunner;
import com.moviearchive.tmdb.TmdbClient;
import com.moviearchive.tmdb.TmdbMatcher;
import javafx.concurrent.Task;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates a full library scan (one or more root folders) as a
 * background JavaFX Task so the UI thread never blocks. Every file checks
 * the local cache first; TMDB is only ever contacted for files that aren't
 * in the database yet, and lookups run concurrently (bounded) via virtual
 * threads for speed on large archives.
 */
public class ScanPipeline extends Task<ScanPipeline.Summary> {

    private static final int CONCURRENCY = 6;

    public record Summary(int totalFound, int alreadyCached, int autoMatched, int needsReview, int noApiKey) {}

    /** A file/folder that isn't cached yet, with its parsed title/year ready for TMDB lookup. */
    private record WorkItem(String filePath, String rawName, String title, Integer year, String altTitle) {}

    private final List<Path> roots;
    private final MovieRepository repository;
    private final TmdbMatcher matcher; // null if no API key configured yet

    private final MovieNameParser parser = new MovieNameParser();

    public ScanPipeline(Path root, MovieRepository repository, TmdbClient tmdbClient, PosterCache posterCache) {
        this(List.of(root), repository, tmdbClient, posterCache);
    }

    public ScanPipeline(List<Path> roots, MovieRepository repository, TmdbClient tmdbClient, PosterCache posterCache) {
        this.roots = roots;
        this.repository = repository;
        this.matcher = tmdbClient != null ? new TmdbMatcher(tmdbClient, posterCache) : null;
    }

    @Override
    protected Summary call() throws Exception {
        // --- Phase 1: walk every root and collect new (not-yet-cached) items. Fast, no network. ---
        List<WorkItem> newItems = new ArrayList<>();
        int totalFound = 0;
        int cached = 0;

        for (Path root : roots) {
            if (isCancelled()) break;
            updateMessage("در حال پیمایش: " + root);
            List<ArchiveScanner.MovieUnit> units = new ArchiveScanner().scan(root);

            for (ArchiveScanner.MovieUnit unit : units) {
                totalFound++;

                if (unit instanceof ArchiveScanner.ShortcutUnit sc) {
                    updateMessage("رد شد (شورتکات ویندوز): " + sc.lnkFile().getFileName());
                    continue;
                }

                Path videoFile;
                String nameToParse;
                if (unit instanceof ArchiveScanner.FolderUnit fu) {
                    videoFile = fu.videoFile();
                    nameToParse = fu.folder().getFileName().toString();
                } else {
                    ArchiveScanner.FileUnit f = (ArchiveScanner.FileUnit) unit;
                    videoFile = f.videoFile();
                    nameToParse = f.videoFile().getFileName().toString();
                }

                String filePath = videoFile.toAbsolutePath().toString();
                if (repository.existsByFilePath(filePath)) {
                    cached++;
                    continue;
                }

                MovieNameParser.ParseResult parsed = parser.parse(nameToParse);
                newItems.add(new WorkItem(filePath, nameToParse, parsed.cleanTitle(), parsed.year(), parsed.alternateTitle()));
            }
        }

        // --- Phase 2: resolve + save. Sequential (no network) if no API key; concurrent otherwise. ---
        int[] matched = {0};
        int[] review = {0};
        int[] noKey = {0};
        int totalNew = newItems.size();
        AtomicInteger done = new AtomicInteger(0);

        if (matcher == null) {
            for (WorkItem item : newItems) {
                if (isCancelled()) break;
                Movie movie = baseMovie(item);
                movie.setMatchStatus(Movie.MatchStatus.PENDING);
                repository.insert(movie);
                noKey[0]++;
                updateProgress(done.incrementAndGet(), totalNew);
                updateMessage("ذخیره شد بدون متادیتا (کلید TMDB تنظیم نشده): " + item.title());
            }
        } else {
            ConcurrentMatchRunner.runBounded(
                    newItems,
                    CONCURRENCY,
                    item -> matcher.resolve(item.title(), item.year(), item.altTitle()),
                    (item, outcome) -> {
                        Movie movie = baseMovie(item);
                        applyOutcome(movie, outcome);
                        repository.insert(movie);
                        if (outcome.status() == Movie.MatchStatus.MATCHED) matched[0]++; else review[0]++;
                        updateProgress(done.incrementAndGet(), totalNew);
                        updateMessage(outcome.message() + ": " + item.title());
                    },
                    this::isCancelled
            );
        }

        for (Path root : roots) {
            repository.touchScanPath(root.toString());
        }

        return new Summary(totalFound, cached, matched[0], review[0], noKey[0]);
    }

    private Movie baseMovie(WorkItem item) {
        Movie movie = new Movie();
        movie.setFilePath(item.filePath());
        movie.setRawName(item.rawName());
        movie.setParsedTitle(item.title());
        movie.setParsedYear(item.year());
        movie.setTitle(item.title()); // fallback display name until/unless TMDB fills it in
        return movie;
    }

    static void applyOutcome(Movie movie, TmdbMatcher.Outcome outcome) {
        movie.setMatchStatus(outcome.status());
        if (outcome.status() == Movie.MatchStatus.MATCHED) {
            movie.setTmdbId(outcome.tmdbId());
            movie.setTitle(outcome.title());
            movie.setOriginalTitle(outcome.originalTitle());
            movie.setReleaseYear(outcome.releaseYear());
            movie.setOverview(outcome.overview());
            movie.setOverviewFa(outcome.overviewFa());
            movie.setVoteAverage(outcome.voteAverage());
            movie.setVoteCount(outcome.voteCount());
            movie.setRuntimeMinutes(outcome.runtimeMinutes());
            movie.setPosterPath(outcome.localPosterPath());
            movie.getTags().clear();
            movie.getTags().addAll(outcome.tags());
            movie.setDirector(outcome.director());
            movie.setCast(new ArrayList<>(outcome.cast()));
            movie.setTrailerUrl(outcome.trailerUrl());
            movie.setCountry(outcome.country());
            movie.setCountryFa(outcome.countryFa());
            movie.setPopularity(outcome.popularity());
            movie.setCertification(outcome.certification());
            movie.setImdbId(outcome.imdbId());
        }
    }
}
