package com.moviearchive.tmdb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Resolves TMDB matches for many items concurrently using virtual threads
 * (Java 21), bounded to a fixed number of in-flight requests at once. That
 * bound - not a fixed per-item sleep - is what keeps this polite to TMDB's
 * rate limits while still being dramatically faster than one-at-a-time for
 * a large archive.
 */
public final class ConcurrentMatchRunner {

    private ConcurrentMatchRunner() {}

    /** Like BiConsumer, but allowed to throw - database writes (SQLException) happen in here. */
    @FunctionalInterface
    public interface ThrowingBiConsumer<T, U> {
        void accept(T t, U u) throws Exception;
    }

    /**
     * @param items        the work items to resolve (e.g. parsed file names, or existing Movie rows)
     * @param concurrency  max number of TMDB lookups in flight at once
     * @param resolver     turns one item into a TmdbMatcher.Outcome (the network call - never throws, see TmdbMatcher.resolve)
     * @param afterResolve called once per completed item, synchronized across
     *                     threads - do database writes and progress/message
     *                     updates here
     * @param isCancelled  checked before starting each item's work
     * @throws Exception the first error encountered in afterResolve, if any (after all in-flight work settles)
     */
    public static <T> void runBounded(
            List<T> items,
            int concurrency,
            Function<T, TmdbMatcher.Outcome> resolver,
            ThrowingBiConsumer<T, TmdbMatcher.Outcome> afterResolve,
            BooleanSupplier isCancelled) throws Exception {

        if (items.isEmpty()) return;

        Object lock = new Object();
        Semaphore permits = new Semaphore(concurrency);
        List<Exception> errors = new ArrayList<>(); // only ever touched while holding `lock`

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (T item : items) {
                if (isCancelled.getAsBoolean()) break;
                permits.acquire();
                executor.submit(() -> {
                    try {
                        if (!isCancelled.getAsBoolean()) {
                            TmdbMatcher.Outcome outcome = resolver.apply(item);
                            synchronized (lock) {
                                try {
                                    afterResolve.accept(item, outcome);
                                } catch (Exception ex) {
                                    errors.add(ex);
                                }
                            }
                        }
                    } finally {
                        permits.release();
                    }
                });
            }
            // try-with-resources blocks here (ExecutorService.close(), JDK 19+)
            // until all submitted tasks finish, which is exactly what we want.
        }

        if (!errors.isEmpty()) {
            throw errors.get(0);
        }
    }
}
