package com.moviearchive.ui;

import com.moviearchive.model.Movie;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A single poster tile in the library grid, styled loosely after
 * Coollector's poster-wall look. Supports two sizes: COMPACT (just poster +
 * title, dense grid) and LARGE (bigger poster + a director/year subtitle).
 */
public class MovieCard extends VBox {

    public enum Size {
        COMPACT(140, 210, false),
        LARGE(200, 300, true);

        final double width, height;
        final boolean showSubtitle;

        Size(double width, double height, boolean showSubtitle) {
            this.width = width;
            this.height = height;
            this.showSubtitle = showSubtitle;
        }
    }

    private static final Map<Size, Image> placeholderCache = new EnumMap<>(Size.class);

    public MovieCard(Movie movie, Consumer<Movie> onSelect) {
        this(movie, Size.COMPACT, onSelect);
    }

    public MovieCard(Movie movie, Size size, Consumer<Movie> onSelect) {
        setAlignment(Pos.TOP_CENTER);
        setSpacing(6);
        setPrefWidth(size.width);
        getStyleClass().add("movie-card");

        StackPane posterStack = new StackPane();
        ImageView imageView = new ImageView(loadPosterOrPlaceholder(movie, size));
        imageView.setFitWidth(size.width);
        imageView.setFitHeight(size.height);
        imageView.setPreserveRatio(false);
        Rectangle clip = new Rectangle(size.width, size.height);
        clip.setArcWidth(10);
        clip.setArcHeight(10);
        imageView.setClip(clip);
        posterStack.getChildren().add(imageView);

        if (movie.getMatchStatus() == Movie.MatchStatus.NEEDS_REVIEW
                || movie.getMatchStatus() == Movie.MatchStatus.NOT_FOUND) {
            Label flag = new Label("!");
            flag.getStyleClass().add("review-flag");
            StackPane.setAlignment(flag, Pos.TOP_RIGHT);
            posterStack.getChildren().add(flag);
        }

        if (movie.getVoteAverage() != null && movie.getVoteAverage() > 0) {
            Label rating = new Label(String.format("%.1f", movie.getVoteAverage()));
            rating.getStyleClass().add("rating-badge");
            StackPane.setAlignment(rating, Pos.BOTTOM_LEFT);
            posterStack.getChildren().add(rating);
        }

        if (movie.isFavorite()) {
            Label star = new Label("★");
            star.getStyleClass().add("favorite-badge");
            StackPane.setAlignment(star, Pos.TOP_LEFT);
            posterStack.getChildren().add(star);
        }

        if (movie.isWatched()) {
            Label check = new Label("✓");
            check.getStyleClass().add("watched-badge");
            StackPane.setAlignment(check, Pos.BOTTOM_RIGHT);
            posterStack.getChildren().add(check);
        }

        Label title = new Label(displayTitle(movie));
        title.setWrapText(true);
        title.setMaxWidth(size.width);
        title.setAlignment(Pos.CENTER);
        title.setTextAlignment(TextAlignment.CENTER);
        title.setFont(Font.font("System", FontWeight.NORMAL, size.showSubtitle ? 13 : 12));
        title.getStyleClass().add("card-title");
        Tooltip.install(this, new Tooltip(displayTitle(movie)));

        getChildren().addAll(posterStack, title);

        if (size.showSubtitle) {
            String subtitle = subtitleFor(movie);
            if (!subtitle.isBlank()) {
                Label sub = new Label(subtitle);
                sub.setWrapText(true);
                sub.setMaxWidth(size.width);
                sub.setAlignment(Pos.CENTER);
                sub.setTextAlignment(TextAlignment.CENTER);
                sub.getStyleClass().add("card-subtitle");
                getChildren().add(sub);
            }
        }

        // Consume the press so the ListView row this card lives in never
        // sees it and doesn't trigger its own row-selection state change
        // (which was swallowing the first click - see MOUSE_CLICKED below,
        // which still fires normally on this same node afterward).
        setOnMousePressed(javafx.scene.input.MouseEvent::consume);
        setOnMouseClicked(e -> onSelect.accept(movie));
    }

    private String subtitleFor(Movie m) {
        StringBuilder sb = new StringBuilder();
        if (m.getDirector() != null && !m.getDirector().isBlank()) {
            sb.append(m.getDirector());
        }
        return sb.toString();
    }

    private String displayTitle(Movie m) {
        String base = m.getTitle() != null ? m.getTitle() : m.getParsedTitle();
        Integer year = m.getReleaseYear() != null ? m.getReleaseYear() : m.getParsedYear();
        return year != null ? base + " (" + year + ")" : base;
    }

    private Image loadPosterOrPlaceholder(Movie movie, Size size) {
        if (movie.getPosterPath() != null) {
            File f = new File(movie.getPosterPath());
            if (f.exists()) {
                return new Image(f.toURI().toString(), size.width, size.height, false, true, true);
            }
        }
        return placeholder(size);
    }

    /** Generates a simple dark placeholder tile in memory - no bundled asset needed. */
    private Image placeholder(Size size) {
        Image cached = placeholderCache.get(size);
        if (cached != null) return cached;

        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(size.width, size.height);
        var g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#2b2b33"));
        g.fillRect(0, 0, size.width, size.height);
        g.setFill(Color.web("#5a5a66"));
        g.setFont(Font.font(40));
        g.fillText("?", size.width / 2 - 10, size.height / 2);
        javafx.scene.image.WritableImage img = new javafx.scene.image.WritableImage((int) size.width, (int) size.height);
        canvas.snapshot(null, img);
        placeholderCache.put(size, img);
        return img;
    }
}
