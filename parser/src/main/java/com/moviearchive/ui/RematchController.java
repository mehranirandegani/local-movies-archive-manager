package com.moviearchive.ui;

import com.moviearchive.db.MovieRepository;
import com.moviearchive.model.Movie;
import com.moviearchive.tmdb.TmdbMatcher;
import javafx.concurrent.Worker;

import java.util.List;

/**
 * Owns the single "bulk re-match" operation for the whole app. Living at the
 * App level (not inside ReviewQueueDialog) means:
 *   - closing the Review Queue window does NOT stop the operation
 *   - reopening it re-attaches to the still-running task instead of
 *     accidentally starting a second, overlapping one
 *   - there's one real place to call cancel() from ("stop" button)
 */
public class RematchController {

    private final MovieRepository repository;
    private final Runnable onFinished; // e.g. App::loadFromDatabase - always runs, dialog open or not
    private RematchTask currentTask;

    public RematchController(MovieRepository repository, Runnable onFinished) {
        this.repository = repository;
        this.onFinished = onFinished;
    }

    public boolean isRunning() {
        return currentTask != null && currentTask.isRunning();
    }

    public RematchTask getCurrentTask() {
        return currentTask;
    }

    /** Starts a new run, or returns the already-running one if a previous run hasn't finished. */
    public RematchTask start(List<Movie> targets, TmdbMatcher matcher) {
        if (isRunning()) {
            return currentTask;
        }
        RematchTask task = new RematchTask(targets, repository, matcher);
        currentTask = task;

        task.stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED
                    || newState == Worker.State.FAILED
                    || newState == Worker.State.CANCELLED) {
                currentTask = null;
                if (onFinished != null) onFinished.run();
            }
        });

        Thread t = new Thread(task, "rematch-pending");
        t.setDaemon(true);
        t.start();
        return task;
    }

    /** Requests cancellation. The task stops after finishing the movie it's currently on. */
    public void cancel() {
        if (currentTask != null) {
            currentTask.cancel();
        }
    }
}
