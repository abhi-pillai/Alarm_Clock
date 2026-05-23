package org.example;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TimerController {

    @FXML private Label  timerLabel;
    @FXML private Button startPauseBtn;
    @FXML private Label  timerStatusLabel;

    // Total remaining seconds
    private int remainingSeconds = 5 * 60; // Default: 5 minutes
    private boolean running = false;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?>       tickFuture;

    @FXML
    public void initialize() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "timer-thread");
            t.setDaemon(true);
            return t;
        });
        updateDisplay();
    }

    @FXML
    private void handleStartPause() {
        if (running) {
            pauseTimer();
        } else {
            startTimer();
        }
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
                // Timer finished
                Platform.runLater(() -> {
                    running = false;
                    startPauseBtn.setText("Start");
                    timerStatusLabel.setText("Time's up!");
                    SoundEngine.play(null);
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
        remainingSeconds = 5 * 60;
        startPauseBtn.setText("Start");
        timerStatusLabel.setText("");
        updateDisplay();
    }

    // Adjustment buttons — only work when timer is not running
    @FXML private void handlePlusMin()  { adjustTime(+60); }
    @FXML private void handleMinusMin() { adjustTime(-60); }
    @FXML private void handlePlusSec()  { adjustTime(+10); }
    @FXML private void handleMinusSec() { adjustTime(-10); }

    private void adjustTime(int seconds) {
        if (running) return; // Lock adjustments while running
        remainingSeconds = Math.max(0, remainingSeconds + seconds);
        updateDisplay();
    }

    // Formats remainingSeconds as HH:MM:SS and updates the label
    private void updateDisplay() {
        int h = remainingSeconds / 3600;
        int m = (remainingSeconds % 3600) / 60;
        int s = remainingSeconds % 60;
        timerLabel.setText(String.format("%02d:%02d:%02d", h, m, s));
    }

    public void shutdown() {
        if (scheduler != null) scheduler.shutdownNow();
    }
}