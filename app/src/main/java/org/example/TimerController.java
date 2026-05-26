package org.example;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TimerController {

    // ── Timer UI ──
    @FXML private Label  timerLabel;
    @FXML private Button startPauseBtn;
    @FXML private Label  timerStatusLabel;

    // ── Sound picker UI ──
    @FXML private Label                      selectedTuneLabel;
    @FXML private VBox                       soundPickerPanel;
    @FXML private ListView<TuneManager.Tune> tuneListView;

    // ── Timer state ──
    private int             remainingSeconds = 5 * 60;
    private boolean         running          = false;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?>       tickFuture;

    // ── Sound state ──
    private TuneManager.Tune         selectedTune = null;
    private final ObservableList<TuneManager.Tune> tunes =
            FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // ── Timer scheduler ──
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "timer-thread");
            t.setDaemon(true);
            return t;
        });

        // ── Tune list — shared with Alarm tab via same tunes.txt file ──
        tunes.setAll(TuneManager.loadAll());
        tuneListView.setItems(tunes);
        tuneListView.getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, oldVal, newVal) -> {
                if (newVal != null) selectTune(newVal);
            });
        tuneListView.getSelectionModel().selectFirst();
        selectedTune = tunes.isEmpty() ? null : tunes.get(0);

        updateDisplay();
    }

    // ─── Timer controls ──────────────────────────────────────────────

    @FXML
    private void handleStartPause() {
        if (running) pauseTimer();
        else         startTimer();
    }

    private void startTimer() {
        if (remainingSeconds <= 0) return;
        running = true;
        startPauseBtn.setText("Pause");
        timerStatusLabel.setText("Running...");

        tickFuture = scheduler.scheduleAtFixedRate(() -> {
            if (remainingSeconds > 0) {
                remainingSeconds--;
                Platform.runLater(this::updateDisplay);
            } else {
                Platform.runLater(() -> {
                    running = false;
                    startPauseBtn.setText("Start");
                    timerStatusLabel.setText("Time's up!");

                    // Play whichever tune is selected
                    SoundEngine.play(selectedTune);
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
    }

    @FXML
    private void handleReset() {
        pauseTimer();
        SoundEngine.stopCurrent(); // stop sound if timer expired and sound is playing
        remainingSeconds = 5 * 60;
        startPauseBtn.setText("Start");
        timerStatusLabel.setText("");
        updateDisplay();
    }

    @FXML private void handlePlusMin()  { adjustTime(+60); }
    @FXML private void handleMinusMin() { adjustTime(-60); }
    @FXML private void handlePlusSec()  { adjustTime(+10); }
    @FXML private void handleMinusSec() { adjustTime(-10); }

    private void adjustTime(int seconds) {
        if (running) return;
        remainingSeconds = Math.max(0, remainingSeconds + seconds);
        updateDisplay();
    }

    private void updateDisplay() {
        int h = remainingSeconds / 3600;
        int m = (remainingSeconds % 3600) / 60;
        int s = remainingSeconds % 60;
        timerLabel.setText(String.format("%02d:%02d:%02d", h, m, s));
    }

    // ─── Sound picker handlers ────────────────────────────────────────

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
            new FileChooser.ExtensionFilter("Audio files", "*.wav", "*.mp3"),
            new FileChooser.ExtensionFilter("WAV files",   "*.wav"),
            new FileChooser.ExtensionFilter("MP3 files",   "*.mp3")
        );

        File file = chooser.showOpenDialog(
                tuneListView.getScene().getWindow());

        if (file != null) {
            String rawName  = file.getName();
            String tuneName = rawName.contains(".")
                    ? rawName.substring(0, rawName.lastIndexOf('.'))
                    : rawName;

            TuneManager.Tune newTune =
                    new TuneManager.Tune(file.getAbsolutePath(), tuneName);

            boolean exists = tunes.stream()
                    .anyMatch(t -> t.getId().equals(newTune.getId()));

            if (!exists) {
                tunes.add(newTune);
                TuneManager.save(tunes); // persists to same tunes.txt as Alarm tab
                tuneListView.getSelectionModel().select(newTune);
                selectTune(newTune);
            }
        }
    }

    @FXML
    private void handlePreviewTune() {
        TuneManager.Tune tune =
                tuneListView.getSelectionModel().getSelectedItem();
        if (tune != null) SoundEngine.play(tune);
    }

    @FXML
    private void handleRemoveTune() {
        TuneManager.Tune tune =
                tuneListView.getSelectionModel().getSelectedItem();
        if (tune == null || tune.isDefault()) return;

        tunes.remove(tune);
        TuneManager.save(tunes);

        if (tune.equals(selectedTune)) {
            selectTune(tunes.get(0));
            tuneListView.getSelectionModel().selectFirst();
        }
    }

    private void selectTune(TuneManager.Tune tune) {
        selectedTune = tune;
        selectedTuneLabel.setText(tune.getName());
    }

    // ─── Lifecycle ───────────────────────────────────────────────────

    public void shutdown() {
        SoundEngine.stopCurrent();
        if (scheduler != null) scheduler.shutdownNow();
    }
}