package org.example;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TimerController {

    // ── UI ───────────────────────────────────────────────────────────
    @FXML private Canvas  progressCanvas;
    @FXML private Button  startPauseBtn;
    @FXML private Label   timerStatusLabel;
    @FXML private Label   selectedTuneLabel;

    // ── Timer state ──────────────────────────────────────────────────
    private int     remainingSeconds = 5 * 60;
    private int     totalSeconds     = 5 * 60;
    private boolean running          = false;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?>        tickFuture;

    // ── Sound state ──────────────────────────────────────────────────
    // Owns its own tunes list loaded from the same tunes.txt as Alarm
    private TuneManager.Tune selectedTune = null;
    private final ObservableList<TuneManager.Tune> tunes =
            FXCollections.observableArrayList();

    // ── Initialize ───────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Scheduler for countdown
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "timer-thread");
            t.setDaemon(true);
            return t;
        });

        // Load tunes from shared tunes.txt — same file as Alarm tab
        tunes.setAll(TuneManager.loadAll());

        // Default to first tune (built-in beep)
        if (!tunes.isEmpty()) selectTune(tunes.get(0));

        drawProgress();
    }

    // ── Tune selection ───────────────────────────────────────────────

    private void selectTune(TuneManager.Tune tune) {
        selectedTune = tune;
        if (selectedTuneLabel != null)
            selectedTuneLabel.setText(tune.getName());
    }

    // ── Sound picker popup ───────────────────────────────────────────

    @FXML
    private void handleOpenSoundPicker() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/org/example/sound_picker.fxml"));
            Parent root = loader.load();

            SoundPickerController controller =
                    loader.getController();

            // Pass shared tunes list + current selection
            controller.init(tunes, selectedTune);

            Stage popupStage = new Stage();
            popupStage.setTitle("Choose sound");
            popupStage.setScene(new Scene(root));
            popupStage.setResizable(false);

            popupStage.initModality(Modality.WINDOW_MODAL);
            popupStage.initOwner(
                    selectedTuneLabel.getScene().getWindow());

            // On close — stop audio + read back selection
            popupStage.setOnHidden(e -> {
                SoundEngine.stopCurrent();
                TuneManager.Tune picked =
                        controller.getSelectedTune();
                if (picked != null) selectTune(picked);
            });

            popupStage.showAndWait();

        } catch (Exception e) {
            System.err.println(
                    "Failed to open sound picker: "
                    + e.getMessage());
            e.printStackTrace();
        }
    }

    // ── Timer controls ───────────────────────────────────────────────

    @FXML
    private void handleStartPause() {
        if (running) pauseTimer();
        else         startTimer();
    }

    private void startTimer() {
        if (remainingSeconds <= 0) return;

        // Lock total for ring percentage calculation
        totalSeconds = remainingSeconds;
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

    @FXML
    private void handleReset() {
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

    // ── Progress ring drawing ─────────────────────────────────────────

    private void drawProgress() {
        GraphicsContext gc = progressCanvas.getGraphicsContext2D();
        double w  = progressCanvas.getWidth();
        double h  = progressCanvas.getHeight();
        double cx = w / 2;
        double cy = h / 2;
        double r  = Math.min(w, h) / 2 - 14;

        gc.clearRect(0, 0, w, h);

        // Background ring (muted sage)
        gc.setStroke(Color.web("#e8f4f1"));
        gc.setLineWidth(10);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.strokeOval(cx - r, cy - r, r * 2, r * 2);

        // Foreground arc (sage green, shrinks as time passes)
        double progress = totalSeconds > 0
                ? (double) remainingSeconds / totalSeconds
                : 0;

        if (progress > 0) {
            gc.setStroke(Color.web("#4a7c6f"));
            gc.setLineWidth(10);
            gc.setLineCap(StrokeLineCap.ROUND);
            gc.strokeArc(
                cx - r, cy - r, r * 2, r * 2,
                90,             // start at top
                progress * 360, // extent shrinks over time
                ArcType.OPEN
            );
        }

        // Time text
        int hh = remainingSeconds / 3600;
        int mm = (remainingSeconds % 3600) / 60;
        int ss = remainingSeconds % 60;
        String timeStr = String.format(
                "%02d:%02d:%02d", hh, mm, ss);

        gc.setFill(Color.web("#1a1a18"));
        gc.setFont(Font.font(
                "Segoe UI Light", FontWeight.LIGHT, 28));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(timeStr, cx, cy + 10);

        // Sub-label beneath time
        gc.setFill(Color.web("#9a9a94"));
        gc.setFont(Font.font(
                "Segoe UI", FontWeight.NORMAL, 11));
        String subLabel = running
                ? "remaining"
                : remainingSeconds == 0
                        ? "done" : "ready";
        gc.fillText(subLabel, cx, cy + 28);
    }

    // ── Lifecycle ────────────────────────────────────────────────────

    public void shutdown() {
        SoundEngine.stopCurrent();
        if (scheduler != null) scheduler.shutdownNow();
    }
}