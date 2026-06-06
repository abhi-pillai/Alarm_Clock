package org.example;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TimerController {

    // ── FXML ──
    @FXML private HBox   presetsRow;
    @FXML private Canvas progressCanvas;
    @FXML private Button startPauseBtn;
    @FXML private Label  timerStatusLabel;
    @FXML private Label  selectedTuneLabel;
    @FXML private VBox   soundPickerPanel;
    @FXML private ListView<TuneManager.Tune> tuneListView;

    // ── Preset model ──
    private static class Preset {
        final String label;
        final int    seconds;
        final boolean isCustom;

        Preset(String label, int seconds, boolean isCustom) {
            this.label    = label;
            this.seconds  = seconds;
            this.isCustom = isCustom;
        }
    }

    private final List<Preset> presets = new ArrayList<>();
    private Preset activePreset = null;

    // ── Timer state ──
    private int     remainingSeconds = 5 * 60;
    private int     totalSeconds     = 5 * 60;
    private boolean running          = false;
    private boolean soundPlaying     = false;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?>        tickFuture;

    // ── Sound ──
    private TuneManager.Tune selectedTune = null;
    private final ObservableList<TuneManager.Tune> tunes =
            FXCollections.observableArrayList();

    // ─── Initialize ──────────────────────────────────────────────────

    @FXML
    public void initialize() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "timer-thread");
            t.setDaemon(true);
            return t;
        });

        // Default presets
        presets.add(new Preset("5 min",  5  * 60, false));
        presets.add(new Preset("10 min", 10 * 60, false));
        presets.add(new Preset("15 min", 15 * 60, false));

        // Sound
        tunes.setAll(TuneManager.loadAll());
        tuneListView.setItems(tunes);
        tuneListView.getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, o, n) -> { if (n != null) selectTune(n); });
        tuneListView.getSelectionModel().selectFirst();

        // Build preset UI
        buildPresetRow();

        // Select first preset by default
        selectPreset(presets.get(0));
    }

    // ─── Preset row builder ───────────────────────────────────────────

    private void buildPresetRow() {
        // Clear everything except the + button (always last)
        presetsRow.getChildren().clear();

        for (Preset preset : presets) {
            presetsRow.getChildren().add(makePresetButton(preset));
        }

        // + Add button always at the end
        Button addBtn = new Button("+");
        addBtn.getStyleClass().add("add-preset-btn");
        addBtn.setPrefWidth(64);
        addBtn.setPrefHeight(64);
        addBtn.setOnAction(e -> handleAddPreset());
        presetsRow.getChildren().add(addBtn);
    }

    private Button makePresetButton(Preset preset) {
        // Two-line button: "5\nmin" or "20\nCustom"
        int    mins  = preset.seconds / 60;
        int    secs  = preset.seconds % 60;
        String top   = secs == 0
                ? String.valueOf(mins)
                : String.format("%d:%02d", mins, secs);
        String sub   = preset.isCustom ? "Custom" : "min";

        Label topLbl = new Label(top);
        topLbl.getStyleClass().add("preset-time-lbl");

        Label subLbl = new Label(sub);
        subLbl.getStyleClass().add("preset-sub-lbl");

        VBox box = new VBox(2, topLbl, subLbl);
        box.setAlignment(Pos.CENTER);

        Button btn = new Button();
        btn.setGraphic(box);
        btn.getStyleClass().add("preset-btn");
        btn.setPrefWidth(64);
        btn.setPrefHeight(64);

        btn.setOnAction(e -> selectPreset(preset));

        // Context menu to delete custom presets
        if (preset.isCustom) {
            ContextMenu menu = new ContextMenu();
            MenuItem del = new MenuItem("Remove preset");
            del.setOnAction(e -> {
                presets.remove(preset);
                buildPresetRow();
                if (activePreset == preset) {
                    selectPreset(presets.get(0));
                }
            });
            menu.getItems().add(del);
            btn.setContextMenu(menu);
        }

        return btn;
    }

    private void selectPreset(Preset preset) {
        if (running) return; // Don't switch while running

        activePreset     = preset;
        remainingSeconds = preset.seconds;
        totalSeconds     = preset.seconds;
        soundPlaying     = false;

        // Update button styles
        presetsRow.getChildren().forEach(node -> {
            if (node instanceof Button btn &&
                    btn.getStyleClass().contains("preset-btn")) {
                btn.getStyleClass().removeAll("preset-btn-active");
            }
        });

        // Find and highlight the active preset button
        int idx = presets.indexOf(preset);
        if (idx >= 0 && idx < presetsRow.getChildren().size()) {
            presetsRow.getChildren().get(idx)
                    .getStyleClass().add("preset-btn-active");
        }

        startPauseBtn.setText("▶");
        timerStatusLabel.setText("");
        drawProgress();
    }

    // ─── Add custom preset ────────────────────────────────────────────

    @FXML
    private void handleAddPreset() {
        // Ask for minutes via a simple dialog
        TextInputDialog dialog = new TextInputDialog("20");
        dialog.setTitle("Custom preset");
        dialog.setHeaderText("Enter duration in minutes");
        dialog.setContentText("Minutes:");

        // Style the dialog
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/org/example/style.css")
                          .toExternalForm());

        dialog.showAndWait().ifPresent(input -> {
            try {
                int mins = Integer.parseInt(input.trim());
                if (mins <= 0 || mins > 999) {
                    showError("Enter a number between 1 and 999.");
                    return;
                }
                Preset custom = new Preset(mins + " min",
                        mins * 60, true);
                presets.add(custom);
                buildPresetRow();
                selectPreset(custom);
            } catch (NumberFormatException e) {
                showError("Please enter a valid number.");
            }
        });
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    // ─── Timer controls ──────────────────────────────────────────────

    @FXML
    private void handleStartPause() {
        if (soundPlaying) return; // Don't start while sound is playing

        if (running) {
            pauseTimer();
        } else {
            startTimer();
        }
    }

    private void startTimer() {
        if (remainingSeconds <= 0) return;

        running = true;
        startPauseBtn.setText("⏸");
        timerStatusLabel.setText("");

        tickFuture = scheduler.scheduleAtFixedRate(() -> {
            if (remainingSeconds > 0) {
                remainingSeconds--;
                Platform.runLater(this::drawProgress);
            } else {
                // Timer finished
                Platform.runLater(() -> {
                    running      = false;
                    soundPlaying = true;
                    startPauseBtn.setText("▶");
                    timerStatusLabel.setText("Time's up! Press Stop to dismiss.");
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
        startPauseBtn.setText("▶");
        timerStatusLabel.setText("Paused");
        drawProgress();
    }

    // Stop button — stops sound if playing, otherwise acts as pause
    @FXML
    private void handleStop() {
        if (soundPlaying) {
            // Timer finished and sound is playing — stop the sound
            SoundEngine.stopCurrent();
            soundPlaying = false;
            timerStatusLabel.setText("Stopped.");
        } else {
            // Timer still running — stop and reset
            pauseTimer();
            SoundEngine.stopCurrent();
            timerStatusLabel.setText("Stopped.");
        }
        drawProgress();
    }

    // Reset — goes back to preset duration (or 0 for custom if removed)
    @FXML
    private void handleReset() {
        if (running) pauseTimer();
        SoundEngine.stopCurrent();
        soundPlaying = false;

        if (activePreset != null) {
            remainingSeconds = activePreset.seconds;
            totalSeconds     = activePreset.seconds;
        } else {
            remainingSeconds = 0;
            totalSeconds     = 0;
        }

        startPauseBtn.setText("▶");
        timerStatusLabel.setText("");
        drawProgress();
    }

    // ─── Progress ring ────────────────────────────────────────────────

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

        // Progress arc
        double progress = totalSeconds > 0
                ? (double) remainingSeconds / totalSeconds : 0;

        if (progress > 0) {
            gc.setStroke(soundPlaying
                    ? Color.web("#c0392b")   // red when time's up
                    : Color.web("#4a7c6f")); // sage when running
            gc.setLineWidth(10);
            gc.setLineCap(StrokeLineCap.ROUND);
            gc.strokeArc(
                    cx - r, cy - r, r * 2, r * 2,
                    90, progress * 360,
                    ArcType.OPEN);
        }

        // Time text
        int h2  = remainingSeconds / 3600;
        int m   = (remainingSeconds % 3600) / 60;
        int s   = remainingSeconds % 60;
        String timeStr = h2 > 0
                ? String.format("%02d:%02d:%02d", h2, m, s)
                : String.format("%02d:%02d", m, s);

        gc.setFill(Color.web("#1a1a18"));
        gc.setFont(Font.font("Segoe UI Light", FontWeight.LIGHT, 30));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(timeStr, cx, cy + 10);

        // Sub-label
        String sub = soundPlaying ? "time's up"
                : running        ? "running"
                : remainingSeconds == 0 ? "done"
                : "ready";

        gc.setFill(Color.web("#9a9a94"));
        gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        gc.fillText(sub, cx, cy + 28);
    }

    // ─── Sound picker ────────────────────────────────────────────────

    @FXML
    private void handleOpenSoundPicker() {
        boolean show = !soundPickerPanel.isVisible();
        soundPickerPanel.setVisible(show);
        soundPickerPanel.setManaged(show);
        if (show && selectedTune != null)
            tuneListView.getSelectionModel().select(selectedTune);
    }

    @FXML
    private void handleAddTune() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select alarm tune");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Audio", "*.wav", "*.mp3"),
            new FileChooser.ExtensionFilter("WAV",   "*.wav"),
            new FileChooser.ExtensionFilter("MP3",   "*.mp3")
        );
        File file = chooser.showOpenDialog(
                progressCanvas.getScene().getWindow());
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
            }
            tuneListView.getSelectionModel().select(t);
        }
    }

    @FXML
    private void handlePreviewTune() {
        TuneManager.Tune t =
                tuneListView.getSelectionModel().getSelectedItem();
        if (t != null) SoundEngine.play(t);
    }

    @FXML
    private void handleRemoveTune() {
        TuneManager.Tune t =
                tuneListView.getSelectionModel().getSelectedItem();
        if (t == null || t.isDefault()) return;
        tunes.remove(t);
        TuneManager.save(tunes);
        if (t.equals(selectedTune)) {
            tuneListView.getSelectionModel().selectFirst();
        }
    }

    private void selectTune(TuneManager.Tune tune) {
        selectedTune = tune;
        selectedTuneLabel.setText(tune.getName());
    }

    // ─── Lifecycle ────────────────────────────────────────────────────

    public void shutdown() {
        SoundEngine.stopCurrent();
        if (scheduler != null) scheduler.shutdownNow();
    }
}