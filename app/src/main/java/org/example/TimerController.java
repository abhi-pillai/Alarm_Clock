package org.example;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TimerController {

    // ── UI ───────────────────────────────────────────────────────────
    @FXML private Canvas       progressCanvas;
    @FXML private Button       startPauseBtn;
    @FXML private Button       stopMusicBtn;
    @FXML private Label        timerStatusLabel;
    @FXML private Label        selectedTuneLabel;
    @FXML private HBox         adjRow;          // fine-adjust row (custom only)

    // Preset toggle buttons
    @FXML private ToggleButton preset5Btn;
    @FXML private ToggleButton preset10Btn;
    @FXML private ToggleButton preset15Btn;

    // ── Timer state ──────────────────────────────────────────────────
    private int     remainingSeconds = 5 * 60;   // default: 5 min preset
    private int     totalSeconds     = 5 * 60;
    private boolean running          = false;

    /**
     * activePreset holds the preset's duration in seconds when a preset
     * is selected (300 / 600 / 900), or -1 for a custom timer.
     */
    private int activePreset = 300;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?>        tickFuture;

    // ── Sound state ──────────────────────────────────────────────────
    private TuneManager.Tune selectedTune = null;
    private final ObservableList<TuneManager.Tune> tunes =
            FXCollections.observableArrayList();

    // ── Initialize ───────────────────────────────────────────────────

    @FXML
    public void initialize() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "timer-thread");
            t.setDaemon(true);
            return t;
        });

        tunes.setAll(TuneManager.loadAll());
        if (!tunes.isEmpty()) selectTune(tunes.get(0));

        // Reflect initial selection (5 min) in the UI
        syncPresetButtons();
        syncAdjRow();
        drawProgress();
    }

    // ── Preset handlers ──────────────────────────────────────────────

    @FXML private void handlePreset5()  { applyPreset(300); }
    @FXML private void handlePreset10() { applyPreset(600); }
    @FXML private void handlePreset15() { applyPreset(900); }

    private void applyPreset(int seconds) {
        if (running) return;            // ignore while countdown is live
        activePreset     = seconds;
        remainingSeconds = seconds;
        totalSeconds     = seconds;
        timerStatusLabel.setText("");
        startPauseBtn.setText("▶  Start");
        stopMusicBtn.setDisable(true);
        syncPresetButtons();
        syncAdjRow();
        drawProgress();
    }

    // ── Custom timer dialog ──────────────────────────────────────────

    @FXML
    private void handleAddCustom() {
        if (running) return;

        Dialog<Integer> dialog = new Dialog<>();
        dialog.setTitle("Custom timer");
        dialog.setHeaderText("Set a custom duration");

        ButtonType okType = new ButtonType("Set timer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        Spinner<Integer> minSpinner = new Spinner<>(0, 999, 0);
        Spinner<Integer> secSpinner = new Spinner<>(0,  59, 0);
        minSpinner.setEditable(true);
        minSpinner.setPrefWidth(80);
        secSpinner.setEditable(true);
        secSpinner.setPrefWidth(80);

        HBox row = new HBox(8,
                new Label("Minutes:"), minSpinner,
                new Label("Seconds:"), secSpinner);
        row.setAlignment(Pos.CENTER);
        dialog.getDialogPane().setContent(row);

        dialog.setResultConverter(btn ->
                btn == okType
                        ? minSpinner.getValue() * 60 + secSpinner.getValue()
                        : null);

        Optional<Integer> result = dialog.showAndWait();
        result.ifPresent(total -> {
            if (total <= 0) return;
            activePreset     = -1;          // marks as custom
            remainingSeconds = total;
            totalSeconds     = total;
            timerStatusLabel.setText("");
            startPauseBtn.setText("▶  Start");
            stopMusicBtn.setDisable(true);
            syncPresetButtons();
            syncAdjRow();
            drawProgress();
        });
    }

    // ── UI sync helpers ──────────────────────────────────────────────

    /** Keep the three toggle buttons in sync with activePreset. */
    private void syncPresetButtons() {
        preset5Btn.setSelected(activePreset == 300);
        preset10Btn.setSelected(activePreset == 600);
        preset15Btn.setSelected(activePreset == 900);
    }

    /**
     * Fine-adjustment row is shown (and takes layout space) only when
     * a custom duration is active, so preset users don't see clutter.
     */
    private void syncAdjRow() {
        boolean custom = (activePreset == -1);
        adjRow.setVisible(custom);
        adjRow.setManaged(custom);
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
                    getClass().getResource("/org/example/sound_picker.fxml"));
            Parent root = loader.load();

            SoundPickerController controller = loader.getController();
            controller.init(tunes, selectedTune);

            Stage popup = new Stage();
            popup.setTitle("Choose sound");
            popup.setScene(new Scene(root));
            popup.setResizable(false);
            popup.initModality(Modality.WINDOW_MODAL);
            popup.initOwner(selectedTuneLabel.getScene().getWindow());

            popup.setOnHidden(e -> {
                SoundEngine.stopCurrent();
                TuneManager.Tune picked = controller.getSelectedTune();
                if (picked != null) selectTune(picked);
            });

            popup.showAndWait();

        } catch (Exception e) {
            System.err.println("Failed to open sound picker: " + e.getMessage());
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

        totalSeconds = remainingSeconds;     // lock ring baseline
        running      = true;
        startPauseBtn.setText("⏸  Pause");
        timerStatusLabel.setText("");

        tickFuture = scheduler.scheduleAtFixedRate(() -> {
            if (remainingSeconds > 0) {
                remainingSeconds--;
                Platform.runLater(this::drawProgress);
            } else {
                Platform.runLater(() -> {
                    running = false;
                    startPauseBtn.setText("▶  Start");
                    timerStatusLabel.setText("Time's up!");
                    stopMusicBtn.setDisable(false);  // let user silence alarm
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
        startPauseBtn.setText("▶  Resume");
        timerStatusLabel.setText("Paused");
        drawProgress();
    }

    /**
     * Reset behaviour:
     *   • Preset timer  → restores to that preset's duration (300 / 600 / 900 s)
     *   • Custom timer  → resets to 0 (user must re-enter a duration)
     */
    @FXML
    private void handleReset() {
        pauseTimer();
        SoundEngine.stopCurrent();
        stopMusicBtn.setDisable(true);
        timerStatusLabel.setText("");
        startPauseBtn.setText("▶  Start");

        if (activePreset > 0) {
            remainingSeconds = activePreset;
            totalSeconds     = activePreset;
        } else {
            remainingSeconds = 0;
            totalSeconds     = 0;
        }
        drawProgress();
    }

    /** Silences the alarm that fires when the countdown reaches zero. */
    @FXML
    private void handleStopMusic() {
        SoundEngine.stopCurrent();
        stopMusicBtn.setDisable(true);
        timerStatusLabel.setText("");
    }

    // ── Fine-adjustment (custom timers only) ─────────────────────────

    @FXML private void handlePlusMin()  { adjustTime(+60); }
    @FXML private void handleMinusMin() { adjustTime(-60); }
    @FXML private void handlePlusSec()  { adjustTime(+10); }
    @FXML private void handleMinusSec() { adjustTime(-10); }

    private void adjustTime(int delta) {
        if (running) return;
        remainingSeconds = Math.max(0, remainingSeconds + delta);
        totalSeconds     = remainingSeconds;
        drawProgress();
    }

    // ── Progress ring drawing ─────────────────────────────────────────

    private void drawProgress() {
        GraphicsContext gc = progressCanvas.getGraphicsContext2D();
        double w  = progressCanvas.getWidth();
        double h  = progressCanvas.getHeight();
        double cx = w / 2, cy = h / 2;
        double r  = Math.min(w, h) / 2 - 14;

        gc.clearRect(0, 0, w, h);

        // Track ring (muted sage)
        gc.setStroke(Color.web("#e8f4f1"));
        gc.setLineWidth(10);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.strokeOval(cx - r, cy - r, r * 2, r * 2);

        // Progress arc (shrinks as time elapses)
        double progress = totalSeconds > 0
                ? (double) remainingSeconds / totalSeconds : 0;
        if (progress > 0) {
            gc.setStroke(Color.web("#4a7c6f"));
            gc.setLineWidth(10);
            gc.setLineCap(StrokeLineCap.ROUND);
            gc.strokeArc(cx - r, cy - r, r * 2, r * 2,
                    90, progress * 360, ArcType.OPEN);
        }

        // Time text
        int hh = remainingSeconds / 3600;
        int mm = (remainingSeconds % 3600) / 60;
        int ss = remainingSeconds % 60;
        gc.setFill(Color.web("#1a1a18"));
        gc.setFont(Font.font("Segoe UI Light", FontWeight.LIGHT, 28));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(String.format("%02d:%02d:%02d", hh, mm, ss), cx, cy + 10);

        // Sub-label
        gc.setFill(Color.web("#9a9a94"));
        gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        String sub = running ? "remaining" : remainingSeconds == 0 ? "done" : "ready";
        gc.fillText(sub, cx, cy + 28);
    }

    // ── Lifecycle ────────────────────────────────────────────────────

    public void shutdown() {
        SoundEngine.stopCurrent();
        if (scheduler != null) scheduler.shutdownNow();
    }
}