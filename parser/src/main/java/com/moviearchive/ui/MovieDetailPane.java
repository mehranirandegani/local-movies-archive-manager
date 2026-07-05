package com.moviearchive.ui;

import com.moviearchive.config.AppConfig;
import com.moviearchive.model.Movie;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.function.Consumer;

/**
 * Right-hand panel showing full details for the currently selected movie.
 * Two-column layout:
 *   column 1 (narrow)  - poster, personal fields (watched/favorite), play/trailer buttons
 *   column 2 (wide)    - title, meta line, director/country/certification, full overview,
 *                         cast, tags, and the less-frequent maintenance actions (fix match / refresh)
 * The whole thing sits in a ScrollPane so nothing gets cut off regardless of window height.
 *
 * Cast names, director names, and tags are clickable: clicking a person's
 * name fills the search box with that name; clicking a tag switches the
 * sidebar's tag filter to just that tag. Neither action changes what's
 * shown here - the detail pane keeps showing this same movie until the
 * user picks a different one from the grid/list.
 */
public class MovieDetailPane extends ScrollPane {

    private static final double POSTER_WIDTH = 220;
    private static final double POSTER_HEIGHT = 330;

    private final AppConfig config;

    private final ImageView poster = new ImageView();
    private final Label titleLabel = new Label();
    private final FlowPane metaLabel = new FlowPane();
    private final Label countryLabel = new Label();
    private final FlowPane directorPane = new FlowPane();
    private final Label overviewLabel = new Label();
    private final VBox castPane = new VBox(4);
    private final FlowPane tagsPane = new FlowPane();
    private final Button playButton = new Button(Strings.playMovie());
    private final Button trailerButton = new Button(Strings.trailerOrImdb());
    private final Button fixMatchButton = new Button(Strings.fixMatch());
    private final Button refreshButton = new Button(Strings.refreshMetadata());
    private final CheckBox watchedBox = new CheckBox(Strings.watchedLabel());
    private final ToggleButton favoriteBtn = new ToggleButton(Strings.favoriteLabel());
    private final Label emptyState = new Label(Strings.selectMoviePrompt());
    private final Label pathLabel = new Label();

    private Movie current;
    private boolean suppressPersonalFieldEvents;
    private Consumer<Movie> onFixMatchRequested;
    private Consumer<Movie> onPersonalFieldsChanged;
    private Consumer<Movie> onRefreshRequested;
    private Consumer<String> onPersonSearchRequested;
    private Consumer<String> onTagFilterRequested;

    public MovieDetailPane(AppConfig config) {
        this.config = config;
        setFitToWidth(true);
        setPrefWidth(560);
        getStyleClass().add("detail-scroll");
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);

        poster.setFitWidth(POSTER_WIDTH);
        poster.setFitHeight(POSTER_HEIGHT);
        poster.setPreserveRatio(false);

        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 20));
        titleLabel.setWrapText(true);

        metaLabel.setHgap(6);
        metaLabel.setVgap(2);

        countryLabel.getStyleClass().add("meta-label");
        countryLabel.setWrapText(true);

        directorPane.setHgap(4);
        directorPane.setVgap(2);

        overviewLabel.setWrapText(true);
        overviewLabel.setTextAlignment(TextAlignment.LEFT);
        overviewLabel.setMaxWidth(Double.MAX_VALUE);

        tagsPane.setHgap(6);
        tagsPane.setVgap(6);

        playButton.setMaxWidth(Double.MAX_VALUE);
        playButton.getStyleClass().add("btn-primary");
        playButton.setOnAction(e -> playCurrentFile());

        trailerButton.setMaxWidth(Double.MAX_VALUE);
        trailerButton.getStyleClass().add("btn-secondary");
        trailerButton.setOnAction(e -> openTrailer());

        fixMatchButton.setMaxWidth(Double.MAX_VALUE);
        fixMatchButton.getStyleClass().add("btn-secondary");
        fixMatchButton.setOnAction(e -> {
            if (current != null && onFixMatchRequested != null) onFixMatchRequested.accept(current);
        });

        refreshButton.setMaxWidth(Double.MAX_VALUE);
        refreshButton.getStyleClass().add("btn-secondary");
        refreshButton.setOnAction(e -> {
            if (current != null && onRefreshRequested != null) onRefreshRequested.accept(current);
        });

        favoriteBtn.getStyleClass().add("btn-secondary");
        favoriteBtn.setMaxWidth(Double.MAX_VALUE);
        watchedBox.setOnAction(e -> savePersonalFields());
        favoriteBtn.setOnAction(e -> savePersonalFields());

        pathLabel.setWrapText(true);
        pathLabel.getStyleClass().add("path-label");

        emptyState.setAlignment(Pos.CENTER);
        emptyState.setMaxWidth(Double.MAX_VALUE);
        emptyState.setMaxHeight(Double.MAX_VALUE);

        // Force-wrap everything to the pane's actual width instead of letting
        // Labels/FlowPanes compute an "unwrapped natural width" - that natural
        // width (not the maxWidth cap) is what was causing horizontal scrolling.
        widthProperty().addListener((obs, oldW, newW) -> updateWrapWidths(newW.doubleValue()));
        updateWrapWidths(getPrefWidth());

        showEmpty();
    }

    private void updateWrapWidths(double totalWidth) {
        if (totalWidth <= 0) totalWidth = getPrefWidth();
        double fullWidth = Math.max(200, totalWidth - 55);   // minus root padding + scrollbar allowance
        double narrowWidth = Math.max(140, fullWidth - POSTER_WIDTH - 20); // minus column1 + HBox spacing

        titleLabel.setPrefWidth(fullWidth);
        metaLabel.setPrefWrapLength(fullWidth);

        overviewLabel.setPrefWidth(narrowWidth);
        directorPane.setPrefWrapLength(narrowWidth);
        castPane.setMaxWidth(narrowWidth);
        tagsPane.setPrefWrapLength(narrowWidth);
    }

    public void setOnFixMatchRequested(Consumer<Movie> handler) {
        this.onFixMatchRequested = handler;
    }

    /** Called whenever the user toggles "watched" or "favorite" - the caller should persist it. */
    public void setOnPersonalFieldsChanged(Consumer<Movie> handler) {
        this.onPersonalFieldsChanged = handler;
    }

    /** Called when the user asks to re-fetch this movie's TMDB metadata (poster is reused from cache). */
    public void setOnRefreshRequested(Consumer<Movie> handler) {
        this.onRefreshRequested = handler;
    }

    /** Called with a plain person name when the user clicks a cast/director link. */
    public void setOnPersonSearchRequested(Consumer<String> handler) {
        this.onPersonSearchRequested = handler;
    }

    /** Called with a tag name when the user clicks a tag chip. */
    public void setOnTagFilterRequested(Consumer<String> handler) {
        this.onTagFilterRequested = handler;
    }

    public void showEmpty() {
        VBox wrapper = new VBox(emptyState);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setPrefHeight(400);
        setContent(wrapper);
    }

    public void show(Movie movie) {
        this.current = movie;
        String lang = config.getAppLanguage();

        if (movie.getPosterPath() != null && new File(movie.getPosterPath()).exists()) {
            poster.setImage(new Image(new File(movie.getPosterPath()).toURI().toString(), POSTER_WIDTH, POSTER_HEIGHT, false, true, true));
        } else {
            poster.setImage(null);
        }

        titleLabel.setText(movie.getTitle() != null ? movie.getTitle() : movie.getParsedTitle());

        metaLabel.getChildren().clear();
        Integer year = movie.getReleaseYear() != null ? movie.getReleaseYear() : movie.getParsedYear();
        if (year != null) addMetaPart(String.valueOf(year));
        if (movie.getRuntimeMinutes() != null && movie.getRuntimeMinutes() > 0) {
            addMetaPart(movie.getRuntimeMinutes() + " " + Strings.minutesSuffix());
        }
        if (movie.getVoteAverage() != null && movie.getVoteAverage() > 0) {
            addMetaPart(Strings.ratingPrefix() + " " + String.format("%.1f", movie.getVoteAverage()));
        }
        if (movie.getCertification() != null && !movie.getCertification().isBlank()) {
            addMetaPart(movie.getCertification());
        }

        // Director(s) - each one is its own clickable Hyperlink
        directorPane.getChildren().clear();
        if (movie.getDirector() != null && !movie.getDirector().isBlank()) {
            Label prefix = new Label(Strings.directorLabel());
            prefix.getStyleClass().add("meta-label");
            directorPane.getChildren().add(prefix);
            for (String name : movie.getDirector().split("\\s*,\\s*")) {
                if (name.isBlank()) continue;
                directorPane.getChildren().add(makePersonLink(name.trim()));
            }
        }

        String country = Localization.displayCountry(movie, lang);
        countryLabel.setText(country != null && !country.isBlank() ? Strings.countryFieldLabel() + " " + country : "");
        countryLabel.setManaged(!countryLabel.getText().isEmpty());
        countryLabel.setVisible(!countryLabel.getText().isEmpty());

        String overviewText = Localization.displayOverview(movie, lang);
        overviewLabel.setText(overviewText != null && !overviewText.isBlank() ? overviewText : statusMessage(movie));

        // Cast - each actor is its own clickable Hyperlink (search uses the plain name, without "(Character)")
        castPane.getChildren().clear();
        if (movie.getCast() != null && !movie.getCast().isEmpty()) {
            Label prefix = new Label(Strings.castLabel());
            prefix.getStyleClass().add("meta-label");
            castPane.getChildren().add(prefix);
            for (String display : movie.getCast()) {
                String plainName = stripCharacterSuffix(display);
                Hyperlink link = makePersonLink(plainName);
                link.setText(truncateForDisplay(display)); // show "Name (Character)" but search by plain name
                link.setMaxWidth(280);
                Tooltip.install(link, new Tooltip(display));
                castPane.getChildren().add(link);
            }
        }

        tagsPane.getChildren().clear();
        for (String tag : movie.getTags()) {
            Label chip = new Label(Localization.displayTag(tag, lang));
            chip.getStyleClass().addAll("tag-chip", "tag-chip-clickable");
            chip.setCursor(Cursor.HAND);
            chip.setOnMouseClicked(e -> {
                if (onTagFilterRequested != null) onTagFilterRequested.accept(tag);
            });
            tagsPane.getChildren().add(chip);
        }

        suppressPersonalFieldEvents = true;
        watchedBox.setSelected(movie.isWatched());
        favoriteBtn.setSelected(movie.isFavorite());
        suppressPersonalFieldEvents = false;

        pathLabel.setText(movie.getFilePath());

        // --- Column 1: poster + personal fields + primary actions ---
        HBox personalRow = new HBox(10, watchedBox, favoriteBtn);
        VBox column1 = new VBox(10, poster, personalRow, playButton);
        column1.setPrefWidth(POSTER_WIDTH);
        column1.setMinWidth(POSTER_WIDTH);
        if (movie.getTrailerUrl() != null && !movie.getTrailerUrl().isBlank()) {
            column1.getChildren().add(trailerButton);
        }

        // --- Column 2: text content + secondary (maintenance) actions ---
        VBox column2 = new VBox(8);
        column2.getChildren().add(overviewLabel);
        if (!directorPane.getChildren().isEmpty()) column2.getChildren().add(directorPane);
        if (!countryLabel.getText().isEmpty()) column2.getChildren().add(countryLabel);
        if (!castPane.getChildren().isEmpty()) column2.getChildren().add(castPane);
        column2.getChildren().add(tagsPane);
        HBox.setHgrow(column2, Priority.ALWAYS);

        HBox maintenanceRow = new HBox(8);
        if (movie.getMatchStatus() == Movie.MatchStatus.NEEDS_REVIEW
                || movie.getMatchStatus() == Movie.MatchStatus.NOT_FOUND
                || movie.getMatchStatus() == Movie.MatchStatus.PENDING) {
            maintenanceRow.getChildren().add(fixMatchButton);
        }
        if (movie.getTmdbId() != null) {
            maintenanceRow.getChildren().add(refreshButton);
        }

        HBox columns = new HBox(20, column1, column2);

        VBox root = new VBox(10, titleLabel, metaLabel, columns);
        if (!maintenanceRow.getChildren().isEmpty()) root.getChildren().add(maintenanceRow);
        root.getChildren().add(pathLabel);
        root.setPadding(new Insets(18));

        setContent(root);
    }

    /** "Tom Hanks (Forrest Gump)" -> "Tom Hanks" - we search by the actor's name, not the character. */
    private String stripCharacterSuffix(String display) {
        int idx = display.indexOf(" (");
        return idx > 0 ? display.substring(0, idx).trim() : display.trim();
    }

    private static final int MAX_CAST_DISPLAY_LENGTH = 45;

    /** Trims very long cast entries (e.g. a long character description) so one entry can't force horizontal overflow. Full text is still shown as a tooltip. */
    private String truncateForDisplay(String text) {
        if (text == null || text.length() <= MAX_CAST_DISPLAY_LENGTH) return text;
        return text.substring(0, MAX_CAST_DISPLAY_LENGTH - 1).trim() + "…";
    }

    private Hyperlink makePersonLink(String plainName) {
        Hyperlink link = new Hyperlink(plainName);
        link.getStyleClass().add("person-link");
        link.setOnAction(e -> {
            if (onPersonSearchRequested != null) onPersonSearchRequested.accept(plainName);
        });
        return link;
    }

    /**
     * Adds one meta field (year, runtime, rating, certification) as its own
     * Label in the metaLabel FlowPane, with a "·" separator before it if
     * it's not the first. Keeping each field in its own Label - instead of
     * concatenating everything into one string - avoids the Unicode bidi
     * algorithm reordering multiple English/number "islands" inside an RTL
     * paragraph (this is what caused fields to visually scramble before).
     */
    private void addMetaPart(String text) {
        if (!metaLabel.getChildren().isEmpty()) {
            Label dot = new Label("·");
            dot.getStyleClass().add("meta-label");
            metaLabel.getChildren().add(dot);
        }
        Label part = new Label(text);
        part.getStyleClass().add("meta-label");
        metaLabel.getChildren().add(part);
    }

    private void savePersonalFields() {
        if (suppressPersonalFieldEvents || current == null) return;
        current.setWatched(watchedBox.isSelected());
        current.setFavorite(favoriteBtn.isSelected());
        if (onPersonalFieldsChanged != null) onPersonalFieldsChanged.accept(current);
    }

    private String statusMessage(Movie movie) {
        return switch (movie.getMatchStatus()) {
            case PENDING -> Strings.statusPending();
            case NEEDS_REVIEW -> Strings.statusNeedsReview();
            case NOT_FOUND -> Strings.statusNotFound();
            default -> "";
        };
    }

    private void playCurrentFile() {
        if (current == null || current.getFilePath() == null) return;
        try {
            File file = new File(current.getFilePath());
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
            }
        } catch (Exception ex) {
            overviewLabel.setText(Strings.fileOpenError(ex.getMessage()));
        }
    }

    private void openTrailer() {
        if (current == null || current.getTrailerUrl() == null) return;
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(current.getTrailerUrl()));
            }
        } catch (Exception ignored) {
        }
    }
}