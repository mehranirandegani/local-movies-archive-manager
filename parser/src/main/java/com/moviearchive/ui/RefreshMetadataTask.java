package com.moviearchive.ui;

import com.moviearchive.db.MovieRepository;
import com.moviearchive.model.Movie;
import com.moviearchive.tmdb.TmdbMatcher;
import javafx.concurrent.Task;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Re-fetches TMDB metadata (overview, rating, cast, director, trailer, tags,
 * country, certification...) for movies that already have a confirmed
 * tmdbId - without re-searching and, crucially, without re-downloading the
 * poster: PosterCache already skips the download when a file for that
 * tmdbId exists on disk, so this is exactly "update the data, keep the
 * poster" that new features benefit from without a full re-scan.
 */
public class RefreshMetadataTask extends Task<RefreshMetadataTask.Summary> {

    private static final int CONCURRENCY = 6;

    public record Summary(int total, int updated, int failed) {}

    private final List<Movie> targets;
    private final MovieRepository repository;
    private final TmdbMatcher matcher;

    public RefreshMetadataTask(List<Movie> targets, MovieRepository repository, TmdbMatcher matcher) {
        this.targets = targets;
        this.repository = repository;
        this.matcher = matcher;
    }

    @Override
    protected Summary call() throws Exception {
        int total = targets.size();
        int[] updated = {0};
        int[] failed = {0};
        AtomicInteger done = new AtomicInteger(0);
        Object lock = new Object();
        Semaphore permits = new Semaphore(CONCURRENCY);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Movie movie : targets) {
                if (isCancelled()) break;
                if (movie.getTmdbId() == null) continue;
                permits.acquire();
                executor.submit(() -> {
                    try {
                        if (!isCancelled()) {
                            try {
                                TmdbMatcher.Outcome outcome = matcher.fetchConfirmedMatch(movie.getTmdbId());
                                synchronized (lock) {
                                    Movie.MatchStatus originalStatus = movie.getMatchStatus();
                                    ScanPipeline.applyOutcome(movie, outcome);
                                    movie.setMatchStatus(originalStatus); // keep MANUAL vs MATCHED as it was
                                    repository.update(movie);
                                    updated[0]++;
                                    updateProgress(done.incrementAndGet(), total);
                                    updateMessage("به‌روزرسانی شد: " + movie.getTitle());
                                }
                            } catch (Exception ex) {
                                synchronized (lock) {
                                    failed[0]++;
                                    updateProgress(done.incrementAndGet(), total);
                                    updateMessage("خطا در به‌روزرسانی: " + movie.getTitle());
                                }
                            }
                        }
                    } finally {
                        permits.release();
                    }
                });
            }
        }

        return new Summary(total, updated[0], failed[0]);
    }
}
