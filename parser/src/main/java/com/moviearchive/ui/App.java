package com.moviearchive.ui;

import com.moviearchive.config.AppConfig;
import com.moviearchive.db.MovieRepository;
import com.moviearchive.media.PosterCache;
import com.moviearchive.model.Movie;
import com.moviearchive.tmdb.TmdbClient;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Entry point. Layout:
 *   top    -> toolbar (add/scan folder, settings, review queue, view mode, sort, search, progress)
 *   right* -> unified "Filters" panel (genre + tags multi-select, favorite/watched, advanced ranges)
 *   center -> virtualized poster grid or detailed list (ListView-based, so only visible rows are ever built)
 *   left*  -> movie detail pane (two columns, scrollable)
 *   (* BorderPane.setLeft/setRight are mirrored visually under RTL - setLeft
 *      ends up on the visual right edge and vice versa. Under LTR (English),
 *      they are NOT mirrored, so setLeft stays visually left. Either way,
 *      the Filters panel and the detail pane swap sides consistently with
 *      the app's current reading direction.)
 *
 * Run with: mvn javafx:run   (see pom.xml)
 */
public class App extends Application {

    private AppConfig config;
    private MovieRepository repository;
    private TmdbClient tmdbClient;   // null until an API key is configured
    private PosterCache posterCache; // null until an API key is configured

    private final ObservableList<Movie> allMovies = FXCollections.observableArrayList();
    private final StackPane centerStack = new StackPane();
    private final ListView<List<Movie>> gridListView = new ListView<>();
    private final ListView<Movie> detailListView = new ListView<>();
    private MovieDetailPane detailPane; // needs `config`, so built in start() rather than as a field initializer
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label("");
    private final TextField searchField = new TextField();
    private final Button clearSearchBtn = new Button("×");
    private final ComboBox<String> viewModeBox = new ComboBox<>();
    private final ListView<String> genreList = new ListView<>();
    private final ListView<String> tagList = new ListView<>();
    private final TextField tagSearchField = new TextField();
    private final ToggleButton favoriteFilterBtn = new ToggleButton();
    private final ToggleButton watchedFilterBtn = new ToggleButton();
    private final Button reviewQueueBtn = new Button();
    private final ComboBox<String> sortBox = new ComboBox<>();

    // Advanced filter controls (embedded directly in the Filters panel, not a popup)
    private final TextField yearFromField = new TextField();
    private final TextField yearToField = new TextField();
    private final TextField ratingFromField = new TextField();
    private final TextField ratingToField = new TextField();
    private final TextField runtimeFromField = new TextField();
    private final TextField runtimeToField = new TextField();
    private final ComboBox<String> countryBox = new ComboBox<>();
    private final ComboBox<String> certBox = new ComboBox<>();

    private final Set<String> selectedTags = new LinkedHashSet<>();
    private final Set<String> selectedGenres = new LinkedHashSet<>();
    private Stage primaryStage;
    private RematchController rematchController;
    private List<String> allTags = List.of();
    private List<String> allGenres = List.of();
    private Map<String, String> countryTranslations = Map.of(); // English -> Persian
    private final FilterCriteria advancedFilter = new FilterCriteria();
    private MovieCard.Size currentCardSize = MovieCard.Size.COMPACT;
    private Movie currentlySelectedMovie; // tracked so the grid can scroll back to it once a filter is cleared
    private boolean restoringSavedSettings; // true while applying saved view/sort mode at startup, to avoid re-saving them immediately

    // Stable, language-independent keys - what's actually stored/compared/saved.
    // Their on-screen text comes from Strings.* via a localized cell factory (see sortDisplay/viewModeDisplay).
    private static final String SORT_TITLE = "TITLE";
    private static final String SORT_YEAR = "YEAR";
    private static final String SORT_RATING = "RATING";
    private static final String SORT_ADDED = "ADDED";

    private static final String VIEW_COMPACT = "COMPACT";
    private static final String VIEW_LARGE = "LARGE";
    private static final String VIEW_LIST = "LIST";

    @Override
    public void start(Stage stage) throws Exception {
        this.primaryStage = stage;
        config = new AppConfig();
        Strings.setLanguage(config.getAppLanguage());
        repository = new MovieRepository(config.getDbPath());
        rematchController = new RematchController(repository, this::loadFromDatabase);
        detailPane = new MovieDetailPane(config);
        initTmdbClientIfConfigured();

        BorderPane root = new BorderPane();
        root.setTop(buildToolbar(stage));
        root.setLeft(buildFiltersPanel());
        root.setCenter(buildCenter());
        root.setRight(detailPane);

        detailPane.setOnFixMatchRequested(this::openReviewDialog);
        detailPane.setOnPersonalFieldsChanged(this::savePersonalFields);
        detailPane.setOnRefreshRequested(this::refreshSingleMovie);
        detailPane.setOnPersonSearchRequested(name -> searchField.setText(name));
        detailPane.setOnTagFilterRequested(this::filterByTagOnly);

        restoreSavedViewSettings();

        Scene scene = new Scene(root, config.getWindowWidth(), config.getWindowHeight());
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        scene.setNodeOrientation(currentOrientation());
        stage.setScene(scene);
        stage.setTitle(Strings.appTitle());
        stage.setMaximized(config.isWindowMaximized());
        stage.show();

        loadFromDatabase();

        if (tmdbClient == null) {
            statusLabel.setText(Strings.needTmdbKeyFirst());
        }
    }

    private NodeOrientation currentOrientation() {
        return Localization.FA.equals(config.getAppLanguage())
                ? NodeOrientation.RIGHT_TO_LEFT
                : NodeOrientation.LEFT_TO_RIGHT;
    }

    /**
     * Closes this app instance cleanly and starts a brand new one, so every
     * static piece of UI text and the RTL/LTR layout direction gets rebuilt
     * from scratch in the newly chosen language. Simpler and far more
     * reliable than trying to live-reflow dozens of already-built controls.
     */
    private void restartApp() {
        try {
            this.stop();
        } catch (Exception ignored) {
        }
        Stage oldStage = primaryStage;
        Stage newStage = new Stage();
        try {
            new App().start(newStage);
            oldStage.close();
        } catch (Exception e) {
            statusLabel.setText(Strings.restartFailed(e.getMessage()));
        }
    }

    /** Applies the view mode / sort mode saved from last run, if any, without re-triggering a save. */
    private void restoreSavedViewSettings() {
        restoringSavedSettings = true;
        String savedView = config.getViewMode();
        if (List.of(VIEW_COMPACT, VIEW_LARGE, VIEW_LIST).contains(savedView)) {
            viewModeBox.setValue(savedView);
        }
        String savedSort = config.getSortMode();
        if (List.of(SORT_TITLE, SORT_YEAR, SORT_RATING, SORT_ADDED).contains(savedSort)) {
            sortBox.setValue(savedSort);
        }
        restoringSavedSettings = false;
    }

    private String sortDisplay(String key) {
        return switch (key) {
            case SORT_YEAR -> Strings.sortYear();
            case SORT_RATING -> Strings.sortRating();
            case SORT_ADDED -> Strings.sortAdded();
            default -> Strings.sortTitle();
        };
    }

    private String viewModeDisplay(String key) {
        return switch (key) {
            case VIEW_LARGE -> Strings.viewLarge();
            case VIEW_LIST -> Strings.viewList();
            default -> Strings.viewCompact();
        };
    }

    /** Generic "show a localized label for a stable key" cell, used for sort/view mode combo boxes. */
    private static class KeyDisplayCell extends ListCell<String> {
        private final java.util.function.Function<String, String> displayFn;

        KeyDisplayCell(java.util.function.Function<String, String> displayFn) {
            this.displayFn = displayFn;
        }

        @Override
        protected void updateItem(String key, boolean empty) {
            super.updateItem(key, empty);
            setText(empty || key == null ? null : displayFn.apply(key));
        }
    }

    private HBox buildToolbar(Stage stage) {
        Button addFolderBtn = new Button(Strings.addScanFolder());
        addFolderBtn.getStyleClass().add("btn-primary");
        addFolderBtn.setOnAction(e -> chooseAndScanFolder(stage));

        Button settingsBtn = new Button(Strings.settings());
        settingsBtn.getStyleClass().add("btn-secondary");
        settingsBtn.setOnAction(e -> SettingsDialog.show(
                primaryStage, config, repository,
                () -> tmdbClient, () -> posterCache,
                () -> rematchController.isRunning(),
                this::initTmdbClientIfConfigured,
                this::loadFromDatabase,
                this::replaceRepository,
                this::restartApp
        ));

        reviewQueueBtn.setText(Strings.reviewQueue());
        reviewQueueBtn.getStyleClass().add("btn-secondary");
        reviewQueueBtn.setOnAction(e -> openReviewQueue());

        sortBox.getItems().setAll(SORT_TITLE, SORT_YEAR, SORT_RATING, SORT_ADDED);
        sortBox.setValue(SORT_TITLE);
        sortBox.getStyleClass().add("sort-box");
        sortBox.setCellFactory(lv -> new KeyDisplayCell(this::sortDisplay));
        sortBox.setButtonCell(new KeyDisplayCell(this::sortDisplay));
        sortBox.valueProperty().addListener((obs, old, val) -> {
            applyFilters();
            if (!restoringSavedSettings) {
                config.setSortMode(val);
                config.save();
            }
        });

        viewModeBox.getItems().setAll(VIEW_COMPACT, VIEW_LARGE, VIEW_LIST);
        viewModeBox.setValue(VIEW_COMPACT);
        viewModeBox.getStyleClass().add("sort-box");
        viewModeBox.setCellFactory(lv -> new KeyDisplayCell(this::viewModeDisplay));
        viewModeBox.setButtonCell(new KeyDisplayCell(this::viewModeDisplay));
        viewModeBox.valueProperty().addListener((obs, old, val) -> {
            applyFilters();
            if (!restoringSavedSettings) {
                config.setViewMode(val);
                config.save();
            }
        });

        searchField.setPromptText(Strings.searchPrompt());
        searchField.setMaxWidth(Double.MAX_VALUE);
        searchField.textProperty().addListener((obs, old, val) -> {
            clearSearchBtn.setVisible(val != null && !val.isEmpty());
            applyFilters();
        });

        clearSearchBtn.getStyleClass().add("clear-btn");
        clearSearchBtn.setVisible(false);
        clearSearchBtn.setOnAction(e -> searchField.clear());

        StackPane searchWrapper = new StackPane(searchField, clearSearchBtn);
        StackPane.setAlignment(clearSearchBtn, Pos.CENTER_RIGHT);
        StackPane.setMargin(clearSearchBtn, new Insets(0, 4, 0, 4));
        HBox.setHgrow(searchWrapper, Priority.ALWAYS);

        progressBar.setPrefWidth(160);
        progressBar.setVisible(false);

        HBox bar = new HBox(10, addFolderBtn, settingsBtn, reviewQueueBtn,
                viewModeBox, sortBox, searchWrapper, progressBar, statusLabel);
        bar.setPadding(new Insets(10));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("toolbar");
        return bar;
    }

    // ============================================================
    // Unified "Filters" panel: genre + tags (multi-select) + favorite/watched + advanced ranges
    // ============================================================
    private ScrollPane buildFiltersPanel() {
        Label header = new Label(Strings.filtersHeader());
        header.getStyleClass().add("sidebar-header");

        Label genreHeader = new Label(Strings.genreHeader());
        genreHeader.getStyleClass().add("sidebar-header");
        genreList.setPrefHeight(160);
        genreList.setCellFactory(lv -> new GenreCheckCell());

        Label tagHeader = new Label(Strings.tagsHeader());
        tagHeader.getStyleClass().add("sidebar-header");

        tagSearchField.setPromptText(Strings.tagSearchPrompt());
        tagSearchField.getStyleClass().add("tag-search");
        tagSearchField.textProperty().addListener((obs, old, val) -> filterTagList(val));

        tagList.setPrefHeight(180);
        tagList.setCellFactory(lv -> new TagCheckCell());

        favoriteFilterBtn.setText(Strings.onlyFavorites());
        favoriteFilterBtn.getStyleClass().add("btn-secondary");
        favoriteFilterBtn.setMaxWidth(Double.MAX_VALUE);
        favoriteFilterBtn.setOnAction(e -> applyFilters());

        watchedFilterBtn.setText(Strings.onlyWatched());
        watchedFilterBtn.getStyleClass().add("btn-secondary");
        watchedFilterBtn.setMaxWidth(Double.MAX_VALUE);
        watchedFilterBtn.setOnAction(e -> applyFilters());

        Label advHeader = new Label(Strings.advancedFilter());
        advHeader.getStyleClass().add("sidebar-header");

        yearFromField.setPromptText(Strings.fromYear());
        yearToField.setPromptText(Strings.toYear());
        ratingFromField.setPromptText(Strings.fromRating());
        ratingToField.setPromptText(Strings.toRating());
        runtimeFromField.setPromptText(Strings.fromRuntime());
        runtimeToField.setPromptText(Strings.toRuntime());
        for (TextField f : List.of(yearFromField, yearToField, ratingFromField, ratingToField, runtimeFromField, runtimeToField)) {
            f.setPrefWidth(90);
        }

        yearFromField.textProperty().addListener((o, ov, v) -> { advancedFilter.yearFrom = parseIntOrNull(v); applyFilters(); });
        yearToField.textProperty().addListener((o, ov, v) -> { advancedFilter.yearTo = parseIntOrNull(v); applyFilters(); });
        ratingFromField.textProperty().addListener((o, ov, v) -> { advancedFilter.ratingFrom = parseDoubleOrNull(v); applyFilters(); });
        ratingToField.textProperty().addListener((o, ov, v) -> { advancedFilter.ratingTo = parseDoubleOrNull(v); applyFilters(); });
        runtimeFromField.textProperty().addListener((o, ov, v) -> { advancedFilter.runtimeFrom = parseIntOrNull(v); applyFilters(); });
        runtimeToField.textProperty().addListener((o, ov, v) -> { advancedFilter.runtimeTo = parseIntOrNull(v); applyFilters(); });

        countryBox.getStyleClass().add("sort-box");
        countryBox.setMaxWidth(Double.MAX_VALUE);
        countryBox.getItems().setAll(Strings.any());
        countryBox.setValue(Strings.any());
        countryBox.setCellFactory(lv -> new CountryCell());
        countryBox.setButtonCell(new CountryCell());
        countryBox.valueProperty().addListener((o, ov, v) -> {
            advancedFilter.country = Strings.any().equals(v) ? null : v;
            applyFilters();
        });

        certBox.getStyleClass().add("sort-box");
        certBox.setMaxWidth(Double.MAX_VALUE);
        certBox.getItems().setAll(Strings.any());
        certBox.setValue(Strings.any());
        certBox.valueProperty().addListener((o, ov, v) -> {
            advancedFilter.certification = Strings.any().equals(v) ? null : v;
            applyFilters();
        });

        Button clearAllBtn = new Button(Strings.clearAllFilters());
        clearAllBtn.getStyleClass().add("btn-secondary");
        clearAllBtn.setMaxWidth(Double.MAX_VALUE);
        clearAllBtn.setOnAction(e -> clearAllFilters());

        VBox sidebar = new VBox(8,
                header,
                genreHeader, genreList,
                tagHeader, tagSearchField, tagList,
                favoriteFilterBtn, watchedFilterBtn,
                new Separator(),
                advHeader,
                new HBox(6, yearFromField, yearToField),
                new HBox(6, ratingFromField, ratingToField),
                new HBox(6, runtimeFromField, runtimeToField),
                new Label(Strings.country()), countryBox,
                new Label(Strings.certification()), certBox,
                clearAllBtn);
        sidebar.setPadding(new Insets(10));
        sidebar.setPrefWidth(240);
        sidebar.getStyleClass().add("sidebar");

        ScrollPane scroll = new ScrollPane(sidebar);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("sidebar-scroll");
        return scroll;
    }

    /** Shows a country as "Persian (English)" in Persian mode, or just the English name otherwise. */
    private class CountryCell extends ListCell<String> {
        @Override
        protected void updateItem(String en, boolean empty) {
            super.updateItem(en, empty);
            if (empty || en == null) {
                setText(null);
                return;
            }
            if (Strings.any().equals(en)) {
                setText(en);
                return;
            }
            String fa = countryTranslations.get(en);
            boolean isFa = Localization.FA.equals(config.getAppLanguage());
            setText((isFa && fa != null && !fa.equals(en)) ? fa + " (" + en + ")" : en);
        }
    }

    private void clearAllFilters() {
        selectedTags.clear();
        tagList.refresh();
        selectedGenres.clear();
        genreList.refresh();
        favoriteFilterBtn.setSelected(false);
        watchedFilterBtn.setSelected(false);
        yearFromField.clear();
        yearToField.clear();
        ratingFromField.clear();
        ratingToField.clear();
        runtimeFromField.clear();
        runtimeToField.clear();
        countryBox.setValue(Strings.any());
        certBox.setValue(Strings.any());
        advancedFilter.reset();
        applyFilters();
    }

    /** Replaces the tag/genre filter with just this one - triggered by clicking a chip in the detail pane. */
    private void filterByTagOnly(String tag) {
        selectedTags.clear();
        selectedGenres.clear();
        if (Localization.isGenre(tag)) {
            selectedGenres.add(tag);
        } else {
            selectedTags.add(tag);
        }
        tagSearchField.clear();
        tagList.refresh();
        genreList.refresh();
        applyFilters();
    }

    private Integer parseIntOrNull(String s) {
        try {
            return (s == null || s.isBlank()) ? null : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDoubleOrNull(String s) {
        try {
            return (s == null || s.isBlank()) ? null : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** One tag row with a checkbox - reused across scroll positions, so state is always re-synced from selectedTags. */
    private class TagCheckCell extends ListCell<String> {
        private final CheckBox checkBox = new CheckBox();
        private boolean suppressEvent;

        TagCheckCell() {
            getStyleClass().add("tag-check-cell");
            checkBox.selectedProperty().addListener((obs, old, val) -> {
                if (suppressEvent) return;
                String tag = getItem();
                if (tag == null) return;
                if (val) selectedTags.add(tag); else selectedTags.remove(tag);
                applyFilters();
            });
        }

        @Override
        protected void updateItem(String tag, boolean empty) {
            super.updateItem(tag, empty);
            if (empty || tag == null) {
                setGraphic(null);
                return;
            }
            suppressEvent = true;
            checkBox.setText(Localization.displayTag(tag, config.getAppLanguage()));
            checkBox.setSelected(selectedTags.contains(tag));
            suppressEvent = false;
            setGraphic(checkBox);
        }
    }

    /** Same as TagCheckCell, but for the dedicated genre list (writes to selectedGenres). */
    private class GenreCheckCell extends ListCell<String> {
        private final CheckBox checkBox = new CheckBox();
        private boolean suppressEvent;

        GenreCheckCell() {
            getStyleClass().add("tag-check-cell");
            checkBox.selectedProperty().addListener((obs, old, val) -> {
                if (suppressEvent) return;
                String genre = getItem();
                if (genre == null) return;
                if (val) selectedGenres.add(genre); else selectedGenres.remove(genre);
                applyFilters();
            });
        }

        @Override
        protected void updateItem(String genre, boolean empty) {
            super.updateItem(genre, empty);
            if (empty || genre == null) {
                setGraphic(null);
                return;
            }
            suppressEvent = true;
            checkBox.setText(Localization.displayTag(genre, config.getAppLanguage()));
            checkBox.setSelected(selectedGenres.contains(genre));
            suppressEvent = false;
            setGraphic(checkBox);
        }
    }

    /** Filters which tags are shown in the sidebar list (does not affect the movie grid by itself). */
    private void filterTagList(String query) {
        if (query == null || query.isBlank()) {
            tagList.setItems(FXCollections.observableArrayList(allTags));
            return;
        }
        String q = query.toLowerCase(Locale.ROOT);
        List<String> filtered = allTags.stream()
                .filter(t -> t.toLowerCase(Locale.ROOT).contains(q))
                .toList();
        tagList.setItems(FXCollections.observableArrayList(filtered));
    }

    private StackPane buildCenter() {
        gridListView.getStyleClass().add("virtual-grid");
        gridListView.setCellFactory(lv -> new GridRowCell());
        // Re-chunk into rows whenever the available width changes (e.g. window resize),
        // so the number of cards per row stays sensible instead of a fixed guess.
        gridListView.widthProperty().addListener((obs, oldW, newW) -> {
            if (VIEW_LIST.equals(viewModeBox.getValue())) return;
            if (Math.abs(newW.doubleValue() - oldW.doubleValue()) > 20) applyFilters();
        });

        detailListView.getStyleClass().add("virtual-list");
        detailListView.setCellFactory(lv -> new DetailRowCell());

        centerStack.getChildren().addAll(gridListView, detailListView);
        return centerStack;
    }

    /** One row of poster cards, virtualized by ListView (only visible rows get built). */
    private class GridRowCell extends ListCell<List<Movie>> {
        @Override
        protected void updateItem(List<Movie> row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setGraphic(null);
                return;
            }
            HBox box = new HBox(12);
            box.setPadding(new Insets(6, 16, 6, 16));
            for (Movie m : row) {
                box.getChildren().add(new MovieCard(m, currentCardSize, App.this::selectMovie));
            }
            setGraphic(box);
        }
    }

    /** One row in the detailed-list view, virtualized by ListView (one movie per row). */
    private class DetailRowCell extends ListCell<Movie> {
        @Override
        protected void updateItem(Movie movie, boolean empty) {
            super.updateItem(movie, empty);
            setGraphic(empty || movie == null ? null : new MovieListRow(movie, config.getAppLanguage(), App.this::selectMovie));
        }
    }

    /**
     * The single place a movie becomes "selected": shows it in the detail
     * pane and remembers it so the grid can scroll back to it later (e.g.
     * after the user clicks a cast/tag link, searches around, then clears
     * the search). Clicking a cast/tag link does NOT go through this method
     * - it only changes the search/filter text, so the detail pane keeps
     * showing whatever was selected here until a new card is clicked.
     */
    private void selectMovie(Movie movie) {
        currentlySelectedMovie = movie;
        detailPane.show(movie);
    }

    private void initTmdbClientIfConfigured() {
        java.net.ProxySelector proxySelector = config.toProxySelector();
        // Also covers JavaFX's own Image loading (used for poster thumbnails
        // in ReviewDialog's search results), which uses the classic java.net
        // stack and honors the JVM-wide default ProxySelector.
        java.net.ProxySelector.setDefault(proxySelector);

        if (config.hasTmdbApiKey()) {
            tmdbClient = new TmdbClient(config.getTmdbApiKey(), proxySelector);
            try {
                posterCache = new PosterCache(config.getPosterCacheDir(), proxySelector);
            } catch (Exception e) {
                posterCache = null;
            }
            statusLabel.setText("");
        } else {
            tmdbClient = null;
            posterCache = null;
        }
    }

    /** Called by SettingsDialog after a full-backup restore swaps out the database file. */
    private void replaceRepository(MovieRepository newRepo) {
        this.repository = newRepo;
        this.rematchController = new RematchController(repository, this::loadFromDatabase);
    }

    private void chooseAndScanFolder(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(Strings.chooseArchiveFolder());
        var dir = chooser.showDialog(stage);
        if (dir == null) return;

        try {
            repository.addScanPath(dir.getAbsolutePath());
        } catch (Exception ignored) {
        }
        config.setLastLibraryPath(dir.getAbsolutePath());
        config.save();

        runScan(List.of(Path.of(dir.getAbsolutePath())));
    }

    private void runScan(List<Path> roots) {
        ScanPipeline pipeline = new ScanPipeline(roots, repository, tmdbClient, posterCache);

        progressBar.setVisible(true);
        progressBar.progressProperty().bind(pipeline.progressProperty());
        statusLabel.textProperty().bind(pipeline.messageProperty());

        pipeline.setOnSucceeded(e -> {
            progressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            progressBar.setVisible(false);

            ScanPipeline.Summary s = pipeline.getValue();
            statusLabel.setText(Strings.scanSummary(s.totalFound(), s.alreadyCached(), s.autoMatched(), s.needsReview(), s.noApiKey()));
            loadFromDatabase();
        });

        pipeline.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            progressBar.setVisible(false);
            statusLabel.setText(Strings.scanError(pipeline.getException().getMessage()));
        });

        Thread thread = new Thread(pipeline, "scan-pipeline");
        thread.setDaemon(true);
        thread.start();
    }

    /** Re-fetches TMDB metadata for one movie (reuses its cached poster - see PosterCache). */
    private void refreshSingleMovie(Movie movie) {
        if (tmdbClient == null || movie.getTmdbId() == null) return;
        statusLabel.setText(Strings.refreshingMovie(movie.getTitle()));
        Thread t = new Thread(() -> {
            try {
                com.moviearchive.tmdb.TmdbMatcher matcher = new com.moviearchive.tmdb.TmdbMatcher(tmdbClient, posterCache);
                Movie.MatchStatus originalStatus = movie.getMatchStatus();
                com.moviearchive.tmdb.TmdbMatcher.Outcome outcome = matcher.fetchConfirmedMatch(movie.getTmdbId());
                ScanPipeline.applyOutcome(movie, outcome);
                movie.setMatchStatus(originalStatus);
                repository.update(movie);
                Platform.runLater(() -> {
                    statusLabel.setText(Strings.refreshedMovie(movie.getTitle()));
                    loadFromDatabase();
                    detailPane.show(movie);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText(Strings.refreshError(ex.getMessage())));
            }
        }, "refresh-single-movie");
        t.setDaemon(true);
        t.start();
    }

    private void loadFromDatabase() {
        try {
            List<Movie> movies = repository.findAll();
            allMovies.setAll(movies);
            applyFilters();

            List<String> tags = repository.findAllTagNames();
            allGenres = tags.stream().filter(Localization::isGenre).sorted().toList();
            allTags = tags.stream().filter(t -> !Localization.isGenre(t)).toList();
            genreList.setItems(FXCollections.observableArrayList(allGenres));
            genreList.refresh();
            filterTagList(tagSearchField.getText());
            // Drop selections for tags/genres that no longer exist in the archive
            selectedTags.retainAll(allTags);
            selectedGenres.retainAll(allGenres);
            tagList.refresh();

            countryTranslations = repository.findAllCountries();
            String currentCountry = countryBox.getValue();
            countryBox.getItems().setAll(Strings.any());
            countryBox.getItems().addAll(countryTranslations.keySet());
            countryBox.setValue(countryBox.getItems().contains(currentCountry) ? currentCountry : Strings.any());

            String currentCert = certBox.getValue();
            certBox.getItems().setAll(Strings.any());
            certBox.getItems().addAll(repository.findAllCertifications());
            certBox.setValue(certBox.getItems().contains(currentCert) ? currentCert : Strings.any());

            int pendingCount = repository.findByStatuses(
                    Movie.MatchStatus.PENDING, Movie.MatchStatus.NEEDS_REVIEW, Movie.MatchStatus.NOT_FOUND).size();
            reviewQueueBtn.setText(pendingCount > 0 ? Strings.reviewQueueWithCount(pendingCount) : Strings.reviewQueue());

            // Keep the detail pane's content in sync after data-changing
            // operations (scan, restore, import), without touching it during
            // ordinary search/filter (which never calls this method).
            if (currentlySelectedMovie != null) {
                Movie stillPresent = allMovies.stream().filter(m -> m.equals(currentlySelectedMovie)).findFirst().orElse(null);
                if (stillPresent != null) {
                    currentlySelectedMovie = stillPresent;
                    detailPane.show(stillPresent);
                }
            }
        } catch (Exception e) {
            statusLabel.setText(Strings.loadError(e.getMessage()));
        }
    }

    private void renderGrid(List<Movie> movies) {
        String mode = viewModeBox.getValue();
        if (VIEW_LIST.equals(mode)) {
            detailListView.setItems(FXCollections.observableArrayList(movies));
            detailListView.setVisible(true);
            detailListView.setManaged(true);
            gridListView.setVisible(false);
            gridListView.setManaged(false);
            scrollToSelected(movies, -1);
        } else {
            currentCardSize = VIEW_LARGE.equals(mode) ? MovieCard.Size.LARGE : MovieCard.Size.COMPACT;
            int perRow = computeCardsPerRow(currentCardSize);
            gridListView.setItems(FXCollections.observableArrayList(chunk(movies, perRow)));
            gridListView.setVisible(true);
            gridListView.setManaged(true);
            detailListView.setVisible(false);
            detailListView.setManaged(false);
            scrollToSelected(movies, perRow);
        }
    }

    /**
     * If the currently-selected movie is present in this (freshly filtered)
     * list, scrolls it into view. This is what makes clearing a search/tag
     * filter feel like "returning to where you were" - while actively
     * searching for something else, the selected movie is usually filtered
     * out, so this is naturally a no-op until the filter no longer hides it.
     */
    private void scrollToSelected(List<Movie> movies, int perRow) {
        if (currentlySelectedMovie == null) return;
        int idx = movies.indexOf(currentlySelectedMovie);
        if (idx < 0) return;
        if (perRow <= 0) {
            Platform.runLater(() -> detailListView.scrollTo(idx));
        } else {
            int rowIdx = idx / perRow;
            Platform.runLater(() -> gridListView.scrollTo(rowIdx));
        }
    }

    /** How many cards fit per row given the ListView's current width - keeps the grid responsive to resizing. */
    private int computeCardsPerRow(MovieCard.Size size) {
        double available = gridListView.getWidth();
        if (available <= 1) available = 900; // before the first layout pass has happened
        double cardSlot = size.width + 24; // card width + spacing/padding allowance
        return (int) Math.max(1, Math.floor(available / cardSlot));
    }

    private List<List<Movie>> chunk(List<Movie> source, int size) {
        List<List<Movie>> chunks = new ArrayList<>();
        for (int i = 0; i < source.size(); i += size) {
            chunks.add(source.subList(i, Math.min(i + size, source.size())));
        }
        return chunks;
    }

    private void applyFilters() {
        String q = searchField.getText() == null ? "" : searchField.getText().toLowerCase(Locale.ROOT);
        List<Movie> filtered = allMovies.stream()
                .filter(m -> selectedTags.isEmpty() || m.getTags().containsAll(selectedTags))
                .filter(m -> selectedGenres.isEmpty() || m.getTags().containsAll(selectedGenres))
                .filter(m -> !favoriteFilterBtn.isSelected() || m.isFavorite())
                .filter(m -> !watchedFilterBtn.isSelected() || m.isWatched())
                .filter(advancedFilter::matches)
                .filter(m -> q.isBlank() || matches(m, q))
                .sorted(comparatorFor(sortBox.getValue()))
                .toList();
        renderGrid(filtered);
    }

    private Comparator<Movie> comparatorFor(String sortMode) {
        if (sortMode == null) sortMode = SORT_TITLE;
        return switch (sortMode) {
            case SORT_YEAR -> Comparator.comparing(
                    (Movie m) -> m.getReleaseYear() != null ? m.getReleaseYear() : (m.getParsedYear() != null ? m.getParsedYear() : 0),
                    Comparator.reverseOrder());
            case SORT_RATING -> Comparator.comparing(
                    (Movie m) -> m.getVoteAverage() != null ? m.getVoteAverage() : 0.0,
                    Comparator.reverseOrder());
            case SORT_ADDED -> Comparator.comparing(Movie::getDateAdded, Comparator.reverseOrder());
            default -> Comparator.comparing(
                    (Movie m) -> (m.getTitle() != null ? m.getTitle() : m.getParsedTitle() == null ? "" : m.getParsedTitle())
                            .toLowerCase(Locale.ROOT));
        };
    }

    private boolean matches(Movie m, String q) {
        String title = (m.getTitle() != null ? m.getTitle() : m.getParsedTitle());
        if (title != null && title.toLowerCase(Locale.ROOT).contains(q)) return true;
        if (m.getDirector() != null && m.getDirector().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (m.getOverview() != null && m.getOverview().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (m.getOverviewFa() != null && m.getOverviewFa().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (m.getCast() != null && m.getCast().stream().anyMatch(c -> c.toLowerCase(Locale.ROOT).contains(q))) return true;
        return m.getTags().stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains(q));
    }

    private void savePersonalFields(Movie movie) {
        try {
            repository.update(movie);
        } catch (Exception e) {
            statusLabel.setText(Strings.saveError(e.getMessage()));
        }
    }

    private void openReviewDialog(Movie movie) {
        if (tmdbClient == null) {
            statusLabel.setText(Strings.needTmdbKeyFirst());
            return;
        }
        ReviewDialog dialog = new ReviewDialog(movie, tmdbClient, posterCache, config, resolved -> {
            try {
                repository.update(resolved);
                loadFromDatabase();
            } catch (Exception ex) {
                statusLabel.setText(Strings.saveError(ex.getMessage()));
            }
        });
        dialog.show();
    }

    private void openReviewQueue() {
        try {
            ReviewQueueDialog dialog = new ReviewQueueDialog(repository, tmdbClient, posterCache, config, rematchController, this::loadFromDatabase);
            dialog.show();
        } catch (Exception e) {
            statusLabel.setText(Strings.openReviewQueueError(e.getMessage()));
        }
    }

    @Override
    public void stop() throws Exception {
        if (primaryStage != null && config != null) {
            config.setWindowMaximized(primaryStage.isMaximized());
            if (!primaryStage.isMaximized()) {
                config.setWindowWidth(primaryStage.getWidth());
                config.setWindowHeight(primaryStage.getHeight());
            }
            config.save();
        }
        if (repository != null) repository.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
