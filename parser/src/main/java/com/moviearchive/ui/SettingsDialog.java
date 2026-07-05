package com.moviearchive.ui;

import com.moviearchive.config.AppConfig;
import com.moviearchive.db.ArchiveExporter;
import com.moviearchive.db.FullBackupService;
import com.moviearchive.db.MovieRepository;
import com.moviearchive.media.PosterCache;
import com.moviearchive.model.Movie;
import com.moviearchive.tmdb.TmdbClient;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * One place for everything that used to be scattered across a top toolbar
 * menu and a separate "library folders" dialog: language, TMDB/proxy
 * connection, multi-folder scanning, and archive-wide operations
 * (export/import/backup/bulk metadata refresh).
 */
public class SettingsDialog {

    public static void show(
            Stage owner,
            AppConfig config,
            MovieRepository repository,
            Supplier<TmdbClient> tmdbClientSupplier,
            Supplier<PosterCache> posterCacheSupplier,
            BooleanSupplier isRematchRunning,
            Runnable onTmdbConfigChanged,
            Runnable onDataChanged,
            Consumer<MovieRepository> onRepositoryReplaced,
            Runnable onRestartRequested
    ) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(Strings.settingsTitle());

        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("settings-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab generalTab = new Tab(Strings.tabGeneral(), scrollable(buildGeneralTab(stage, config, onRestartRequested)));
        Tab connectionTab = new Tab(Strings.tabConnection(), scrollable(buildConnectionTab(config, onTmdbConfigChanged)));
        Tab foldersTab = new Tab(Strings.tabFolders(), scrollable(buildFoldersTab(owner, repository, tmdbClientSupplier, posterCacheSupplier, onDataChanged)));
        Tab archiveTab = new Tab(Strings.tabArchive(), scrollable(buildArchiveTab(stage, owner, config, repository, tmdbClientSupplier, posterCacheSupplier,
                isRematchRunning, onDataChanged, onRepositoryReplaced)));

        tabs.getTabs().addAll(generalTab, connectionTab, foldersTab, archiveTab);

        Scene scene = new Scene(tabs, 620, 620);
        scene.setNodeOrientation(Localization.FA.equals(config.getAppLanguage()) ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        scene.getStylesheets().add(SettingsDialog.class.getResource("/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(560);
        stage.setMinHeight(500);
        stage.showAndWait();
    }

    private static ScrollPane scrollable(VBox content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("settings-scroll");
        return sp;
    }

    // ============================================================
    // Tab: General (language)
    // ============================================================
    private static VBox buildGeneralTab(Stage dialogStage, AppConfig config, Runnable onRestartRequested) {
        Label info = new Label(Strings.languageInfo());
        info.setWrapText(true);

        ComboBox<String> languageBox = new ComboBox<>();
        languageBox.getItems().setAll(Strings.languagePersian(), Strings.languageEnglish());
        languageBox.setValue(Localization.FA.equals(config.getAppLanguage()) ? Strings.languagePersian() : Strings.languageEnglish());
        languageBox.getStyleClass().add("sort-box");

        Label savedLabel = new Label();
        savedLabel.setStyle("-fx-text-fill: #6bdc8f;");

        Button saveBtn = new Button(Strings.save());
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setOnAction(e -> {
            String chosen = Strings.languagePersian().equals(languageBox.getValue()) ? Localization.FA : Localization.EN;
            boolean changed = !chosen.equals(config.getAppLanguage());
            config.setAppLanguage(chosen);
            config.save();
            if (changed) {
                dialogStage.close();
                onRestartRequested.run();
            } else {
                savedLabel.setText(Strings.saved());
            }
        });

        VBox box = new VBox(10, info, languageBox, saveBtn, savedLabel);
        box.setPadding(new Insets(16));
        return box;
    }

    // ============================================================
    // Tab 1: TMDB API key + proxy
    // ============================================================
    private static VBox buildConnectionTab(AppConfig config, Runnable onTmdbConfigChanged) {
        Label info = new Label(Strings.tmdbKeyInfo());
        info.setWrapText(true);

        Hyperlink link = new Hyperlink(Strings.getKeyLink());
        link.setOnAction(e -> openBrowser("https://www.themoviedb.org/settings/api"));

        PasswordField keyField = new PasswordField();
        keyField.setText(config.getTmdbApiKey());
        keyField.setPromptText("TMDB API Read Access Token");

        Separator sep = new Separator();

        Label proxyInfo = new Label(Strings.proxyInfo());
        proxyInfo.setWrapText(true);

        CheckBox proxyEnabled = new CheckBox(Strings.useHttpProxy());
        proxyEnabled.setSelected(config.isProxyEnabled());

        TextField hostField = new TextField(config.getProxyHost());
        hostField.setPromptText("127.0.0.1");
        hostField.setPrefWidth(160);

        TextField portField = new TextField(config.getProxyPort() > 0 ? String.valueOf(config.getProxyPort()) : "");
        portField.setPromptText("2081");
        portField.setPrefWidth(90);

        HBox proxyFields = new HBox(8, new Label(Strings.address()), hostField, new Label(Strings.port()), portField);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #e05252;");
        Label savedLabel = new Label();
        savedLabel.setStyle("-fx-text-fill: #6bdc8f;");

        Button saveButton = new Button(Strings.saveConnectionSettings());
        saveButton.getStyleClass().add("btn-primary");
        saveButton.setOnAction(e -> {
            errorLabel.setText("");
            if (proxyEnabled.isSelected()) {
                int port;
                try {
                    port = Integer.parseInt(portField.getText().trim());
                } catch (NumberFormatException ex) {
                    errorLabel.setText(Strings.proxyPortMustBeNumber());
                    return;
                }
                if (hostField.getText().isBlank() || port <= 0) {
                    errorLabel.setText(Strings.proxyFieldsRequired());
                    return;
                }
                config.setProxyHost(hostField.getText().trim());
                config.setProxyPort(port);
            }
            config.setProxyEnabled(proxyEnabled.isSelected());
            config.setTmdbApiKey(keyField.getText());
            config.save();
            onTmdbConfigChanged.run();
            savedLabel.setText(Strings.saved());
        });

        VBox box = new VBox(10, info, link, keyField, sep, proxyInfo, proxyEnabled, proxyFields,
                errorLabel, savedLabel, saveButton);
        box.setPadding(new Insets(16));
        return box;
    }

    // ============================================================
    // Tab 2: Library folders (multi-folder scan)
    // ============================================================
    private static VBox buildFoldersTab(Stage owner, MovieRepository repository,
                                         Supplier<TmdbClient> tmdbClientSupplier, Supplier<PosterCache> posterCacheSupplier,
                                         Runnable onDataChanged) {
        Label info = new Label(Strings.foldersInfo());
        info.setWrapText(true);

        ObservableList<String> paths = FXCollections.observableArrayList();
        ListView<String> listView = new ListView<>(paths);
        listView.setPrefHeight(260);
        VBox.setVgrow(listView, Priority.ALWAYS);

        try {
            paths.setAll(repository.listScanPaths());
        } catch (Exception ignored) {
        }

        Button addBtn = new Button(Strings.addFolder());
        addBtn.getStyleClass().add("btn-secondary");
        addBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(Strings.chooseArchiveFolder());
            var dir = chooser.showDialog(owner);
            if (dir == null) return;
            try {
                repository.addScanPath(dir.getAbsolutePath());
                paths.setAll(repository.listScanPaths());
            } catch (Exception ex) {
                info.setText(Strings.errorPrefix(ex.getMessage()));
            }
        });

        Button removeBtn = new Button(Strings.removeSelected());
        removeBtn.getStyleClass().add("btn-secondary");
        removeBtn.setOnAction(e -> {
            String selected = listView.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            try {
                repository.removeScanPath(selected);
                paths.setAll(repository.listScanPaths());
            } catch (Exception ex) {
                info.setText(Strings.errorPrefix(ex.getMessage()));
            }
        });

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setVisible(false);
        progressBar.setPrefWidth(200);
        Label statusLabel = new Label();

        Button scanAllBtn = new Button(Strings.scanAllFolders());
        scanAllBtn.getStyleClass().add("btn-primary");
        scanAllBtn.setOnAction(e -> {
            TmdbClient client = tmdbClientSupplier.get();
            if (client == null) {
                statusLabel.setText(Strings.needTmdbKeyFirst());
                return;
            }
            if (paths.isEmpty()) return;

            List<Path> roots = paths.stream().map(Path::of).collect(Collectors.toList());
            ScanPipeline pipeline = new ScanPipeline(roots, repository, client, posterCacheSupplier.get());

            progressBar.setVisible(true);
            scanAllBtn.setDisable(true);
            progressBar.progressProperty().bind(pipeline.progressProperty());
            statusLabel.textProperty().bind(pipeline.messageProperty());

            pipeline.setOnSucceeded(ev -> {
                progressBar.progressProperty().unbind();
                statusLabel.textProperty().unbind();
                progressBar.setVisible(false);
                scanAllBtn.setDisable(false);
                ScanPipeline.Summary s = pipeline.getValue();
                statusLabel.setText(Strings.scanSummary(s.totalFound(), s.alreadyCached(), s.autoMatched(), s.needsReview(), s.noApiKey()));
                onDataChanged.run();
            });
            pipeline.setOnFailed(ev -> {
                progressBar.progressProperty().unbind();
                statusLabel.textProperty().unbind();
                progressBar.setVisible(false);
                scanAllBtn.setDisable(false);
                statusLabel.setText(Strings.scanError(pipeline.getException().getMessage()));
            });

            Thread t = new Thread(pipeline, "scan-all-pipeline");
            t.setDaemon(true);
            t.start();
        });

        HBox buttonsRow = new HBox(8, addBtn, removeBtn);
        HBox scanRow = new HBox(10, scanAllBtn, progressBar, statusLabel);

        VBox box = new VBox(10, info, buttonsRow, listView, scanRow);
        box.setPadding(new Insets(16));
        return box;
    }

    // ============================================================
    // Tab 3: Archive-wide operations (export/import/backup/refresh)
    // ============================================================
    private static VBox buildArchiveTab(Stage dialogStage, Stage owner, AppConfig config, MovieRepository repositoryHolder,
                                         Supplier<TmdbClient> tmdbClientSupplier, Supplier<PosterCache> posterCacheSupplier,
                                         BooleanSupplier isRematchRunning, Runnable onDataChanged,
                                         Consumer<MovieRepository> onRepositoryReplaced) {
        // repository can be swapped out (full restore) - keep a mutable holder so the buttons below always use the current one
        MovieRepository[] repoRef = { repositoryHolder };

        Label exportLabel = new Label(Strings.exportImportInfo());
        exportLabel.setWrapText(true);

        Button exportJsonBtn = new Button(Strings.exportJson());
        exportJsonBtn.getStyleClass().add("btn-secondary");
        Button importJsonBtn = new Button(Strings.importJson());
        importJsonBtn.getStyleClass().add("btn-secondary");

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);

        exportJsonBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(Strings.exportJson());
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
            chooser.setInitialFileName("movie-archive-export.json");
            var file = chooser.showSaveDialog(owner);
            if (file == null) return;
            try {
                new ArchiveExporter().exportToJson(repoRef[0].findAll(), file.toPath());
                statusLabel.setText(Strings.saved() + " " + file.getName());
            } catch (Exception ex) {
                statusLabel.setText(Strings.errorPrefix(ex.getMessage()));
            }
        });

        importJsonBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(Strings.importJson());
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
            var file = chooser.showOpenDialog(owner);
            if (file == null) return;
            try {
                ArchiveExporter.ArchiveExport export = new ArchiveExporter().importFromJson(file.toPath());
                int added = 0, skipped = 0;
                for (Movie m : export.movies()) {
                    if (repoRef[0].existsByFilePath(m.getFilePath())) {
                        skipped++;
                        continue;
                    }
                    repoRef[0].insert(m);
                    added++;
                }
                statusLabel.setText(added + " / " + skipped);
                onDataChanged.run();
            } catch (Exception ex) {
                statusLabel.setText(Strings.errorPrefix(ex.getMessage()));
            }
        });

        Separator sep1 = new Separator();

        Label backupLabel = new Label(Strings.backupInfo());
        backupLabel.setWrapText(true);

        Button fullBackupBtn = new Button(Strings.fullBackupExport());
        fullBackupBtn.getStyleClass().add("btn-secondary");
        Button fullRestoreBtn = new Button(Strings.fullBackupRestore());
        fullRestoreBtn.getStyleClass().add("btn-danger");

        fullBackupBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(Strings.fullBackupExport());
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Zip", "*.zip"));
            chooser.setInitialFileName("movie-archive-backup.zip");
            var file = chooser.showSaveDialog(owner);
            if (file == null) return;
            try {
                new FullBackupService().exportFull(config.getDbPath(), config.getPosterCacheDir(), file.toPath());
                statusLabel.setText(Strings.saved() + " " + file.getName());
            } catch (Exception ex) {
                statusLabel.setText(Strings.errorPrefix(ex.getMessage()));
            }
        });

        fullRestoreBtn.setOnAction(e -> {
            if (isRematchRunning.getAsBoolean()) {
                statusLabel.setText(Strings.rematchRunningStopFirst());
                return;
            }
            FileChooser chooser = new FileChooser();
            chooser.setTitle(Strings.fullBackupRestore());
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Zip", "*.zip"));
            var file = chooser.showOpenDialog(owner);
            if (file == null) return;

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    Strings.restoreConfirmMessage(), ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText(Strings.restoreConfirmTitle());
            var result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.YES) return;

            try {
                repoRef[0].close();
                new FullBackupService().importFull(file.toPath(), config.getDbPath(), config.getPosterCacheDir());
                MovieRepository fresh = new MovieRepository(config.getDbPath());
                repoRef[0] = fresh;
                onRepositoryReplaced.accept(fresh);
                onDataChanged.run();
                Alert done = new Alert(Alert.AlertType.INFORMATION, Strings.saved(), ButtonType.OK);
                done.setHeaderText(null);
                done.showAndWait();
                dialogStage.close();
            } catch (Exception ex) {
                statusLabel.setText(Strings.errorPrefix(ex.getMessage()));
            }
        });

        Separator sep2 = new Separator();

        Label refreshLabel = new Label(Strings.refreshAllInfo());
        refreshLabel.setWrapText(true);

        ProgressBar refreshProgress = new ProgressBar(0);
        refreshProgress.setVisible(false);
        refreshProgress.setPrefWidth(200);

        Button refreshAllBtn = new Button(Strings.refreshAllMatched());
        refreshAllBtn.getStyleClass().add("btn-secondary");
        refreshAllBtn.setOnAction(e -> {
            TmdbClient client = tmdbClientSupplier.get();
            if (client == null) {
                statusLabel.setText(Strings.needTmdbKeyFirst());
                return;
            }
            List<Movie> targets;
            try {
                targets = repoRef[0].findAll().stream().filter(m -> m.getTmdbId() != null).toList();
            } catch (Exception ex) {
                statusLabel.setText(Strings.errorPrefix(ex.getMessage()));
                return;
            }
            if (targets.isEmpty()) return;

            com.moviearchive.tmdb.TmdbMatcher matcher = new com.moviearchive.tmdb.TmdbMatcher(client, posterCacheSupplier.get());
            RefreshMetadataTask task = new RefreshMetadataTask(targets, repoRef[0], matcher);

            refreshProgress.setVisible(true);
            refreshAllBtn.setDisable(true);
            refreshProgress.progressProperty().bind(task.progressProperty());
            statusLabel.textProperty().bind(task.messageProperty());

            task.setOnSucceeded(ev -> {
                refreshProgress.progressProperty().unbind();
                statusLabel.textProperty().unbind();
                refreshProgress.setVisible(false);
                refreshAllBtn.setDisable(false);
                RefreshMetadataTask.Summary s = task.getValue();
                statusLabel.setText(String.format(Locale.ROOT, "%d / %d (%d)", s.updated(), s.total(), s.failed()));
                onDataChanged.run();
            });
            task.setOnFailed(ev -> {
                refreshProgress.progressProperty().unbind();
                statusLabel.textProperty().unbind();
                refreshProgress.setVisible(false);
                refreshAllBtn.setDisable(false);
                statusLabel.setText(Strings.errorPrefix(task.getException().getMessage()));
            });

            Thread t = new Thread(task, "refresh-all-metadata");
            t.setDaemon(true);
            t.start();
        });

        VBox box = new VBox(12,
                exportLabel, new HBox(8, exportJsonBtn, importJsonBtn),
                sep1, backupLabel, new HBox(8, fullBackupBtn, fullRestoreBtn),
                sep2, refreshLabel, new HBox(10, refreshAllBtn, refreshProgress),
                statusLabel);
        box.setPadding(new Insets(16));
        return box;
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ignored) {
        }
    }
}
