package com.moviearchive.ui;

import com.moviearchive.config.AppConfig;
import com.moviearchive.db.MovieRepository;
import com.moviearchive.media.PosterCache;
import com.moviearchive.model.Movie;
import com.moviearchive.tmdb.TmdbClient;
import com.moviearchive.tmdb.TmdbMatcher;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;

/**
 * Lists every movie that isn't fully resolved yet:
 *   - PENDING       (scanned before a TMDB key was configured)
 *   - NEEDS_REVIEW  (multiple close candidates - automatic matching held back)
 *   - NOT_FOUND     (TMDB had nothing, or the lookup failed)
 *
 * The bulk re-match runs through a shared RematchController, so it keeps
 * going after this window is closed, and re-opening the queue re-attaches
 * to the same run (with a working Stop button) instead of starting a
 * second, overlapping one.
 */
public class ReviewQueueDialog {

    private final Stage stage;
    private final MovieRepository repository;
    private final TmdbClient tmdbClient; // nullable
    private final PosterCache posterCache; // nullable
    private final AppConfig config;
    private final RematchController controller;
    private final Runnable onChanged;

    private final ListView<Movie> listView = new ListView<>();
    private final Label statusLabel = new Label();
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Button actionBtn = new Button();

    public ReviewQueueDialog(MovieRepository repository, TmdbClient tmdbClient, PosterCache posterCache,
                              AppConfig config, RematchController controller, Runnable onChanged) throws Exception {
        this.repository = repository;
        this.tmdbClient = tmdbClient;
        this.posterCache = posterCache;
        this.config = config;
        this.controller = controller;
        this.onChanged = onChanged;

        this.stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(Strings.reviewQueueTitle());

        refreshList();
        listView.setCellFactory(lv -> new RowCell());

        Button closeBtn = new Button(Strings.close());
        closeBtn.setOnAction(e -> stage.close());

        progressBar.setVisible(false);
        progressBar.setPrefWidth(200);

        HBox top = new HBox(10, actionBtn, progressBar, statusLabel);
        top.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(listView);
        root.setBottom(closeBtn);
        BorderPane.setMargin(closeBtn, new Insets(10));

        syncButtonState();

        stage.setScene(new Scene(root, 520, 560));
        stage.getScene().setNodeOrientation(
                Localization.FA.equals(config.getAppLanguage()) ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        stage.getScene().getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
    }

    public void show() {
        stage.showAndWait();
    }

    private void refreshList() throws Exception {
        List<Movie> items = repository.findByStatuses(
                Movie.MatchStatus.PENDING, Movie.MatchStatus.NEEDS_REVIEW, Movie.MatchStatus.NOT_FOUND);
        listView.setItems(javafx.collections.FXCollections.observableArrayList(items));
    }

    /** Sets the button to either "start" or "stop" depending on whether a run is already active, and (re)binds progress. */
    private void syncButtonState() {
        if (controller.isRunning()) {
            actionBtn.setText(Strings.stop());
            actionBtn.getStyleClass().setAll("btn-danger");
            actionBtn.setOnAction(e -> controller.cancel());
            bindToTask(controller.getCurrentTask());
        } else {
            actionBtn.setText(Strings.rematchAll());
            actionBtn.getStyleClass().setAll("btn-primary");
            actionBtn.setDisable(tmdbClient == null);
            actionBtn.setOnAction(e -> startRematch());
            progressBar.setVisible(false);
            statusLabel.setText(tmdbClient == null ? Strings.needTmdbKeyFirst() : "");
        }
    }

    private void bindToTask(RematchTask task) {
        progressBar.setVisible(true);
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());

        task.stateProperty().addListener((obs, old, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED
                    || newState == javafx.concurrent.Worker.State.FAILED
                    || newState == javafx.concurrent.Worker.State.CANCELLED) {
                progressBar.progressProperty().unbind();
                statusLabel.textProperty().unbind();
                try {
                    refreshList();
                } catch (Exception ignored) {
                }
                syncButtonState();
                onChanged.run();
            }
        });
    }

    private void startRematch() {
        if (tmdbClient == null) return;
        List<Movie> targets = List.copyOf(listView.getItems());
        if (targets.isEmpty()) return;

        TmdbMatcher matcher = new TmdbMatcher(tmdbClient, posterCache);
        controller.start(targets, matcher);
        syncButtonState();
    }

    private void openFixDialog(Movie movie) {
        if (tmdbClient == null) {
            statusLabel.setText(Strings.needTmdbKeyFirst());
            return;
        }
        ReviewDialog dialog = new ReviewDialog(movie, tmdbClient, posterCache, config, resolved -> {
            try {
                repository.update(resolved);
                refreshList();
                onChanged.run();
            } catch (Exception ex) {
                statusLabel.setText(Strings.saveError(ex.getMessage()));
            }
        });
        dialog.show();
    }

    private class RowCell extends ListCell<Movie> {
        @Override
        protected void updateItem(Movie movie, boolean empty) {
            super.updateItem(movie, empty);
            if (empty || movie == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Label label = new Label(statusEmoji(movie) + "  " + movie.getParsedTitle()
                    + (movie.getParsedYear() != null ? " (" + movie.getParsedYear() + ")" : ""));
            label.setMaxWidth(320);
            label.setWrapText(true);

            Button fixBtn = new Button(Strings.fixEllipsis());
            fixBtn.getStyleClass().add("btn-secondary");
            fixBtn.setOnAction(e -> openFixDialog(movie));

            HBox row = new HBox(10, label, fixBtn);
            row.setSpacing(10);
            HBox.setHgrow(label, javafx.scene.layout.Priority.ALWAYS);
            setGraphic(row);
        }

        private String statusEmoji(Movie m) {
            return switch (m.getMatchStatus()) {
                case PENDING -> "⏳";
                case NEEDS_REVIEW -> "❓";
                case NOT_FOUND -> "🚫";
                default -> "";
            };
        }
    }
}
