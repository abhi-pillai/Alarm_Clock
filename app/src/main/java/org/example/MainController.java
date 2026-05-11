package org.example;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainController {

    @FXML
    private Label clockLabel;

    // This formats time as HH:MM:SS (e.g. 14:05:09)
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    // A scheduler that runs tasks on a background thread
    private ScheduledExecutorService scheduler;

    @FXML
    public void initialize() {
        startClock();
    }

    private void startClock() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            // Make this a daemon thread so it doesn't prevent app from closing
            Thread t = new Thread(r, "clock-thread");
            t.setDaemon(true);
            return t;
        });

        // Run every 1 second, starting immediately (delay = 0)
        scheduler.scheduleAtFixedRate(() -> {
            String currentTime = LocalTime.now().format(TIME_FORMAT);

            // Hand the UI update back to JavaFX's UI thread
            Platform.runLater(() -> clockLabel.setText(currentTime));

        }, 0, 1, TimeUnit.SECONDS);
    }
}