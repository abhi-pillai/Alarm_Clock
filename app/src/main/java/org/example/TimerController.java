package org.example;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TimerController {

    @FXML private Canvas  progressCanvas;
    @FXML private Button  startPauseBtn;
    @FXML private Label   timerStatusLabel;

    @FXML private Label                      selectedTuneLabel;
    @FXML private VBox                       soundPickerPanel;
    @FXML private ListView<TuneManager.Tune> tuneListView;

    private int    remainingSeconds = 5 * 60;
    private int    totalSeconds     = 5 * 60; // tracks original for ring %
    private boolean running         = false;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?>        tickFuture;

    private TuneManager.Tune selectedTune = null;
    private final ObservableList<TuneManager.Tune> tunes =
            FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "timer-thread");
            t.setDaemon(true);
            return t;
        });

        tunes.setAll(TuneManager.loadAll());
        tuneListView.setItems(tunes);

        tuneListView.getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, o, n) -> { if (n != null) selectTune(n); });

        tuneListView.getSelectionModel().selectFirst();

        drawProgress();
    }

    // ─── Progress ring drawing ────────────────────────────────────────

    private void drawProgress() {
        GraphicsContext gc = progressCanvas.getGraphicsContext2D();
        double w  = progressCanvas.getWidth();
        double h  = progressCanvas.getHeight();
        double cx = w / 2;
        double cy = h / 2;
        double r  = Math.min(w, h) / 2 - 14;

        gc.clearRect(0, 0, w, h);

        // Background ring
        gc.setStroke(Color.web("#e8f4f1"));
        gc.setLineWidth(10);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.strokeOval(cx - r, cy - r, r * 2, r * 2);

        // Progress arc — sage green
        double progress = totalSeconds > 0
                ? (double) remainingSeconds / totalSeconds : 0;

        if (progress > 0) {
            gc.setStroke(Color.web("#4a7c6f"));
            gc.setLineWidth(10);
            gc.setLineCap(StrokeLineCap.ROUND);

            double startAngle = 90;  // start from top
            double arcExtent  = progress * 360;

            // JavaFX arc: positive = counter-clockwise, so negate
            gc.strokeArc(
                cx - r, cy - r, r * 2, r * 2,
                startAngle, arcExtent,
                javafx.scene.shape.ArcType.OPEN
            );
        }

        // Time text in center
        int h2 = remainingSeconds / 3600;
        int m  = (remainingSeconds % 3600) / 60;
        int s  = remainingSeconds % 60;
        String timeStr = String.format("%02d:%02d:%02d", h2, m, s);

        gc.setFill(Color.web("#1a1a18"));
        gc.setFont(Font.font("Segoe UI Light", FontWeight.LIGHT, 28));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(timeStr, cx, cy + 10);

        // Sub-label
        gc.setFill(Color.web("#9a9a94"));
        gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        gc.fillText(running ? "remaining" : (remainingSeconds == 0 ? "done" : "paused"),
                cx, cy + 28);
    }

    // ─── Timer controls ──────────────────────────────────────────────

    @FXML private void handleStartPause() {
        if (running) pauseTimer(); else startTimer();
    }

    private void startTimer() {
        if (remainingSeconds <= 0) return;
        totalSeconds = remainingSeconds; // lock total for ring calculation
        running      = true;
        startPauseBtn.setText("Pause");
        timerStatusLabel.setText("");

        tickFuture = scheduler.scheduleAtFixedRate(() -> {
            if (remainingSeconds > 0) {
                remainingSeconds--;
                Platform.runLater(this::drawProgress);
            } else {
                Platform.runLater(() -> {
                    running = false;
                    startPauseBtn.setText("Start");
                    timerStatusLabel.setText("Time's up!");
                    SoundEngine.play(selectedTune);
                    drawProgress();
                });
                tickFuture.cancel(false);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void pauseTimer() {
        running = false;
        if (tickFuture != null) tickFuture.cancel(false);
        startPauseBtn.setText("Resume");
        timerStatusLabel.setText("Paused");
        drawProgress();
    }

    @FXML private void handleReset() {
        pauseTimer();
        SoundEngine.stopCurrent();
        remainingSeconds = 5 * 60;
        totalSeconds     = 5 * 60;
        startPauseBtn.setText("Start");
        timerStatusLabel.setText("");
        drawProgress();
    }

    @FXML private void handlePlusMin()  { adjustTime(+60);  }
    @FXML private void handleMinusMin() { adjustTime(-60);  }
    @FXML private void handlePlusSec()  { adjustTime(+10);  }
    @FXML private void handleMinusSec() { adjustTime(-10);  }

    private void adjustTime(int seconds) {
        if (running) return;
        remainingSeconds = Math.max(0, remainingSeconds + seconds);
        totalSeconds     = remainingSeconds;
        drawProgress();
    }

    // ─── Sound picker ────────────────────────────────────────────────

    @FXML private void handleOpenSoundPicker() {
        boolean show = !soundPickerPanel.isVisible();
        soundPickerPanel.setVisible(show);
        soundPickerPanel.setManaged(show);
        if (show && selectedTune != null)
            tuneListView.getSelectionModel().select(selectedTune);
    }

    @FXML private void handleAddTune() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select alarm tune");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Audio files", "*.wav", "*.mp3"),
            new FileChooser.ExtensionFilter("WAV",  "*.wav"),
            new FileChooser.ExtensionFilter("MP3",  "*.mp3")
        );
        File file = chooser.showOpenDialog(tuneListView.getScene().getWindow());
        if (file != null) {
            String raw  = file.getName();
            String name = raw.contains(".")
                    ? raw.substring(0, raw.lastIndexOf('.')) : raw;
            TuneManager.Tune t =
                    new TuneManager.Tune(file.getAbsolutePath(), name);
            boolean exists = tunes.stream()
                    .anyMatch(x -> x.getId().equals(t.getId()));
            if (!exists) {
                tunes.add(t);
                TuneManager.save(tunes);
                tuneListView.getSelectionModel().select(t);
            }
        }
    }

    @FXML private void handlePreviewTune() {
        TuneManager.Tune t =
                tuneListView.getSelectionModel().getSelectedItem();
        if (t != null) SoundEngine.play(t);
    }

    @FXML private void handleRemoveTune() {
        TuneManager.Tune t =
                tuneListView.getSelectionModel().getSelectedItem();
        if (t == null || t.isDefault()) return;
        tunes.remove(t);
        TuneManager.save(tunes);
        if (t.equals(selectedTune)) {
            selectTune(tunes.get(0));
            tuneListView.getSelectionModel().selectFirst();
        }
    }

    private void selectTune(TuneManager.Tune tune) {
        selectedTune = tune;
        selectedTuneLabel.setText(tune.getName());
    }

    public void shutdown() {
        SoundEngine.stopCurrent();
        if (scheduler != null) scheduler.shutdownNow();
    }
}