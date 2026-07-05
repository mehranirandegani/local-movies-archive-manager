package com.moviearchive.ui;

import com.moviearchive.model.Movie;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.File;
import java.util.function.Consumer;

/**
 * A single row in the "detailed list" view style: thumbnail on one side,
 * title/year/director/runtime/rating in the middle, tags and personal-field
 * indicators (favorite/watched) on the other side. Meant for people who want
 * to scan more information per movie than the compact poster grid shows.
 */
public class MovieListRow extends HBox {

    private static final double THUMB_WIDTH = 60;
    private static final double THUMB_HEIGHT = 90;

    public MovieListRow(Movie movie, String language, Consumer<Movie> onSelect) {
        setSpacing(14);
        setPadding(new Insets(8));
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("list-row");

        ImageView thumb = new ImageView(loadThumbOrPlaceholder(movie));
        thumb.setFitWidth(THUMB_WIDTH);
        thumb.setFitHeight(THUMB_HEIGHT);
        Rectangle clip = new Rectangle(THUMB_WIDTH, THUMB_HEIGHT);
        clip.setArcWidth(8);
        clip.setArcHeight(8);
        thumb.setClip(clip);

        Label title = new Label(displayTitle(movie));
        title.setFont(Font.font("System", FontWeight.BOLD, 14));
        title.getStyleClass().add("card-title");

        HBox metaRow = new HBox(6);
        if (movie.getDirector() != null && !movie.getDirector().isBlank()) {
            addMetaPart(metaRow, Strings.directorLabel() + " " + movie.getDirector());
        }
        if (movie.getRuntimeMinutes() != null && movie.getRuntimeMinutes() > 0) {
            addMetaPart(metaRow, movie.getRuntimeMinutes() + " " + Strings.minutesSuffix());
        }
        if (movie.getVoteAverage() != null && movie.getVoteAverage() > 0) {
            addMetaPart(metaRow, Strings.ratingPrefix() + " " + String.format("%.1f", movie.getVoteAverage()));
        }

        FlowPane tagsPane = new FlowPane(4, 4);
        int shown = 0;
        for (String tag : movie.getTags()) {
            if (shown++ >= 6) break;
            Label chip = new Label(Localization.displayTag(tag, language));
            chip.getStyleClass().add("tag-chip");
            tagsPane.getChildren().add(chip);
        }

        VBox textColumn = new VBox(4, title, metaRow, tagsPane);
        HBox.setHgrow(textColumn, Priority.ALWAYS);

        VBox indicators = new VBox(4);
        indicators.setAlignment(Pos.CENTER);
        if (movie.isFavorite()) {
            Label star = new Label(Strings.favoriteIndicator());
            star.getStyleClass().add("favorite-badge");
            indicators.getChildren().add(star);
        }
        if (movie.isWatched()) {
            Label watched = new Label(Strings.watchedIndicator());
            watched.getStyleClass().add("watched-badge");
            indicators.getChildren().add(watched);
        }
        if (movie.getMatchStatus() == Movie.MatchStatus.NEEDS_REVIEW
                || movie.getMatchStatus() == Movie.MatchStatus.NOT_FOUND) {
            Label flag = new Label(Strings.needsReviewIndicator());
            flag.getStyleClass().add("review-flag");
            indicators.getChildren().add(flag);
        }

        getChildren().addAll(thumb, textColumn, indicators);
        // Same fix as MovieCard: consume the press so this row's ListView
        // doesn't intercept it for its own row-selection handling, which
        // was swallowing the first click on a not-yet-selected row.
        setOnMousePressed(javafx.scene.input.MouseEvent::consume);
        setOnMouseClicked(e -> onSelect.accept(movie));
    }

    private String displayTitle(Movie m) {
        String base = m.getTitle() != null ? m.getTitle() : m.getParsedTitle();
        Integer year = m.getReleaseYear() != null ? m.getReleaseYear() : m.getParsedYear();
        return year != null ? base + " (" + year + ")" : base;
    }

    /**
     * Adds one meta field as its own Label, with a "·" separator Label before
     * it if it's not the first. Keeping each field in its own Label (instead
     * of concatenating everything into one string) avoids the Unicode
     * bidi algorithm reordering multiple English/number "islands" inside
     * an RTL paragraph - which is what caused director/runtime/rating to
     * visually scramble when they were all one Label.
     */
    private void addMetaPart(HBox row, String text) {
        if (!row.getChildren().isEmpty()) {
            Label dot = new Label("·");
            dot.getStyleClass().add("meta-label");
            row.getChildren().add(dot);
        }
        Label part = new Label(text);
        part.getStyleClass().add("meta-label");
        row.getChildren().add(part);
    }

    private static Image placeholderCache;

    private Image loadThumbOrPlaceholder(Movie movie) {
        if (movie.getPosterPath() != null) {
            File f = new File(movie.getPosterPath());
            if (f.exists()) {
                return new Image(f.toURI().toString(), THUMB_WIDTH, THUMB_HEIGHT, false, true, true);
            }
        }
        if (placeholderCache == null) {
            javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(THUMB_WIDTH, THUMB_HEIGHT);
            var g = canvas.getGraphicsContext2D();
            g.setFill(javafx.scene.paint.Color.web("#2b2b33"));
            g.fillRect(0, 0, THUMB_WIDTH, THUMB_HEIGHT);
            javafx.scene.image.WritableImage img = new javafx.scene.image.WritableImage((int) THUMB_WIDTH, (int) THUMB_HEIGHT);
            canvas.snapshot(null, img);
            placeholderCache = img;
        }
        return placeholderCache;
    }
}
