package com.moviearchive.ui;

import com.moviearchive.db.MovieRepository;
import com.moviearchive.model.Movie;
import com.moviearchive.tmdb.ConcurrentMatchRunner;
import com.moviearchive.tmdb.TmdbMatcher;
import javafx.concurrent.Task;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Re-runs TMDB matching for movies that were previously stored without
 * metadata (PENDING - scanned before an API key existed) or that failed
 * automatic matching earlier (NEEDS_REVIEW / NOT_FOUND). Does not touch
 * the filesystem at all - it only re-queries TMDB using the title/year
 * already parsed and cached in the database. Runs lookups concurrently
 * (bounded, via virtual threads) for speed on large backlogs.
 */
public class RematchTask extends Task<RematchTask.Summary> {

    private static final int CONCURRENCY = 6;

    public record Summary(int total, int matched, int stillNeedsReview) {}

    private final List<Movie> targets;
    private final MovieRepository repository;
    private final TmdbMatcher matcher;

    public RematchTask(List<Movie> targets, MovieRepository repository, TmdbMatcher matcher) {
        this.targets = targets;
        this.repository = repository;
        this.matcher = matcher;
    }

    @Override
    protected Summary call() throws Exception {
        int total = targets.size();
        int[] matched = {0};
        int[] stillNeeds = {0};
        AtomicInteger done = new AtomicInteger(0);

        ConcurrentMatchRunner.runBounded(
                targets,
                CONCURRENCY,
                movie -> matcher.resolve(movie.getParsedTitle(), movie.getParsedYear(), null),
                (movie, outcome) -> {
                    ScanPipeline.applyOutcome(movie, outcome);
                    repository.update(movie);
                    if (outcome.status() == Movie.MatchStatus.MATCHED) matched[0]++; else stillNeeds[0]++;
                    updateProgress(done.incrementAndGet(), total);
                    updateMessage(outcome.message() + ": " + movie.getParsedTitle());
                },
                this::isCancelled
        );

        return new Summary(total, matched[0], stillNeeds[0]);
    }
}
