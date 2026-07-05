package com.moviearchive.ui;

import com.moviearchive.db.MovieRepository;
import com.moviearchive.media.PosterCache;
import com.moviearchive.tmdb.TmdbClient;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Lets the user register multiple archive root folders (backed by the
 * scan_paths table) and scan all of them together in one pass. Individual
 * "Add & Scan" from the main toolbar still works for a quick one-off scan;
 * this dialog is for people whose movies live across several drives/folders.
 */
public class LibraryFoldersDialog {

    private final Stage stage;
    private final MovieRepository repository;
    private final TmdbClient tmdbClient;
    private final PosterCache posterCache;
    private final Runnable onScanFinished;

    private final ObservableList<String> paths = FXCollections.observableArrayList();
    private final ListView<String> listView = new ListView<>(paths);
    private final Label statusLabel = new Label();
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Button scanAllBtn = new Button("اسکن همه‌ی پوشه‌ها");

    public LibraryFoldersDialog(Stage owner, MovieRepository repository, TmdbClient tmdbClient,
                                 PosterCache posterCache, Runnable onScanFinished) throws Exception {
        this.repository = repository;
        this.tmdbClient = tmdbClient;
        this.posterCache = posterCache;
        this.onScanFinished = onScanFinished;

        this.stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("پوشه‌های آرشیو");

        refreshPaths();

        Button addBtn = new Button("افزودن پوشه...");
        addBtn.getStyleClass().add("btn-secondary");
        addBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("پوشه‌ی آرشیو را انتخاب کنید");
            var dir = chooser.showDialog(owner);
            if (dir == null) return;
            try {
                repository.addScanPath(dir.getAbsolutePath());
                refreshPaths();
            } catch (Exception ex) {
                statusLabel.setText("خطا: " + ex.getMessage());
            }
        });

        Button removeBtn = new Button("حذف انتخاب‌شده");
        removeBtn.getStyleClass().add("btn-secondary");
        removeBtn.setOnAction(e -> {
            String selected = listView.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            try {
                repository.removeScanPath(selected);
                refreshPaths();
            } catch (Exception ex) {
                statusLabel.setText("خطا: " + ex.getMessage());
            }
        });

        scanAllBtn.getStyleClass().add("btn-primary");
        scanAllBtn.setDisable(tmdbClient == null);
        scanAllBtn.setOnAction(e -> scanAll());

        progressBar.setVisible(false);
        progressBar.setPrefWidth(200);

        HBox actions = new HBox(8, addBtn, removeBtn);
        HBox scanRow = new HBox(10, scanAllBtn, progressBar, statusLabel);
        Button closeBtn = new Button("بستن");
        closeBtn.getStyleClass().add("btn-secondary");
        closeBtn.setOnAction(e -> stage.close());

        VBox topBox = new VBox(10, actions, scanRow);
        topBox.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setTop(topBox);
        root.setCenter(listView);
        root.setBottom(closeBtn);
        BorderPane.setMargin(closeBtn, new Insets(10));

        if (tmdbClient == null) {
            statusLabel.setText("برای تطبیق خودکار حین اسکن، اول از «تنظیمات» کلید TMDB را وارد کنید.");
        }

        stage.setScene(new Scene(root, 480, 420));
        stage.getScene().setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        stage.getScene().getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
    }

    public void show() {
        stage.showAndWait();
    }

    private void refreshPaths() throws Exception {
        List<String> all = repository.listScanPaths();
        paths.setAll(all);
    }

    private void scanAll() {
        if (tmdbClient == null || paths.isEmpty()) return;

        List<Path> roots = paths.stream().map(Path::of).collect(Collectors.toList());
        ScanPipeline pipeline = new ScanPipeline(roots, repository, tmdbClient, posterCache);

        progressBar.setVisible(true);
        scanAllBtn.setDisable(true);
        progressBar.progressProperty().bind(pipeline.progressProperty());
        statusLabel.textProperty().bind(pipeline.messageProperty());

        pipeline.setOnSucceeded(e -> {
            progressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            progressBar.setVisible(false);
            scanAllBtn.setDisable(false);

            ScanPipeline.Summary s = pipeline.getValue();
            statusLabel.setText(String.format(Locale.ROOT,
                    "پایان: %d فیلم پیدا شد، %d قبلاً بود، %d تطبیق خودکار، %d نیاز به بررسی",
                    s.totalFound(), s.alreadyCached(), s.autoMatched(), s.needsReview()));
            onScanFinished.run();
        });

        pipeline.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            progressBar.setVisible(false);
            scanAllBtn.setDisable(false);
            statusLabel.setText("خطا: " + pipeline.getException().getMessage());
        });

        Thread t = new Thread(pipeline, "scan-all-pipeline");
        t.setDaemon(true);
        t.start();
    }
}
