package com.moviearchive.ui;

import com.moviearchive.config.AppConfig;
import com.moviearchive.media.PosterCache;
import com.moviearchive.model.Movie;
import com.moviearchive.tmdb.TmdbClient;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;
import java.util.function.Consumer;

/**
 * Lets the user search TMDB manually and pick the correct movie when
 * automatic matching wasn't confident enough. This is the safety valve
 * that keeps the archive accurate instead of silently guessing wrong.
 */
public class ReviewDialog {

    private final Stage stage;
    private final TmdbClient tmdbClient;
    private final PosterCache posterCache;
    private final Movie movie;
    private final Consumer<Movie> onResolved;

    private final ListView<TmdbClient.SearchCandidate> resultsList = new ListView<>();
    private final TextField searchField = new TextField();
    private final Label statusLabel = new Label();

    public ReviewDialog(Movie movie, TmdbClient tmdbClient, PosterCache posterCache, AppConfig config, Consumer<Movie> onResolved) {
        this.movie = movie;
        this.tmdbClient = tmdbClient;
        this.posterCache = posterCache;
        this.onResolved = onResolved;

        this.stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(Strings.fixMatchTitle(movie.getParsedTitle() != null ? movie.getParsedTitle() : movie.getRawName()));

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        searchField.setText(movie.getParsedTitle());
        searchField.setPromptText(Strings.searchTmdbPrompt());
        Button searchButton = new Button(Strings.search());
        searchButton.getStyleClass().add("btn-secondary");
        searchButton.setOnAction(e -> doSearch());
        HBox searchRow = new HBox(8, searchField, searchButton);
        searchField.setOnAction(e -> doSearch());

        resultsList.setCellFactory(list -> new CandidateCell());
        resultsList.setPrefHeight(360);

        Button selectButton = new Button(Strings.selectThisMovie());
        selectButton.getStyleClass().add("btn-primary");
        selectButton.setOnAction(e -> selectCandidate());

        Button skipButton = new Button(Strings.skipKeepNoMetadata());
        skipButton.getStyleClass().add("btn-secondary");
        skipButton.setOnAction(e -> {
            movie.setMatchStatus(Movie.MatchStatus.MANUAL);
            onResolved.accept(movie);
            stage.close();
        });

        HBox actions = new HBox(8, selectButton, skipButton);

        VBox top = new VBox(8, searchRow, statusLabel);
        root.setTop(top);
        root.setCenter(resultsList);
        root.setBottom(actions);
        BorderPane.setMargin(resultsList, new Insets(8, 0, 8, 0));

        stage.setScene(new Scene(root, 460, 520));
        stage.getScene().setNodeOrientation(
                Localization.FA.equals(config.getAppLanguage()) ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        stage.getScene().getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        doSearch();
    }

    public void show() {
        stage.showAndWait();
    }

    private void doSearch() {
        statusLabel.setText(Strings.searching());
        resultsList.getItems().clear();
        new Thread(() -> {
            try {
                List<TmdbClient.SearchCandidate> results = tmdbClient.search(searchField.getText(), movie.getParsedYear());
                javafx.application.Platform.runLater(() -> {
                    resultsList.getItems().setAll(results);
                    statusLabel.setText(results.isEmpty() ? Strings.noResultsFound() : Strings.resultsFoundCount(results.size()));
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> statusLabel.setText(Strings.errorPrefix(ex.getMessage())));
            }
        }).start();
    }

    private void selectCandidate() {
        TmdbClient.SearchCandidate selected = resultsList.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        statusLabel.setText(Strings.fetchingInfo());
        new Thread(() -> {
            try {
                com.moviearchive.tmdb.TmdbMatcher matcher = new com.moviearchive.tmdb.TmdbMatcher(tmdbClient, posterCache);
                com.moviearchive.tmdb.TmdbMatcher.Outcome outcome = matcher.fetchConfirmedMatch(selected.tmdbId());
                ScanPipeline.applyOutcome(movie, outcome);
                movie.setMatchStatus(Movie.MatchStatus.MANUAL); // user picked it, not the automatic matcher

                javafx.application.Platform.runLater(() -> {
                    onResolved.accept(movie);
                    stage.close();
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> statusLabel.setText(Strings.errorPrefix(ex.getMessage())));
            }
        }).start();
    }

    /** Small list cell showing a thumbnail + title/year for each search candidate. */
    private class CandidateCell extends ListCell<TmdbClient.SearchCandidate> {
        private final ImageView thumb = new ImageView();
        private final Label label = new Label();
        private final HBox box = new HBox(8, thumb, label);

        CandidateCell() {
            thumb.setFitWidth(46);
            thumb.setFitHeight(68);
        }

        @Override
        protected void updateItem(TmdbClient.SearchCandidate item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            String year = (item.releaseDate() != null && item.releaseDate().length() >= 4)
                    ? item.releaseDate().substring(0, 4) : "?";
            label.setText(item.title() + " (" + year + ")"
                    + (item.originalTitle() != null && !item.originalTitle().equals(item.title())
                        ? "\n" + item.originalTitle() : ""));
            String posterUrl = tmdbClient.posterUrl(item.posterPath());
            thumb.setImage(posterUrl != null ? new Image(posterUrl, 46, 68, false, true, true) : null);
            setGraphic(box);
        }
    }
}
