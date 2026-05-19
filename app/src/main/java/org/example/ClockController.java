package org.example;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClockController {

    @FXML private Label clockLabel;
    @FXML private Label dateLabel;
    @FXML private Label tzLabel;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEEE, dd MMM");

    private ScheduledExecutorService scheduler;

    @FXML
    public void initialize() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "clock-thread");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            ZonedDateTime now = ZonedDateTime.now();
            String time = now.format(TIME_FMT);
            String date = now.format(DATE_FMT);
            // e.g. "IST (UTC+5:30)"
            String tz   = now.getZone().getDisplayName(
                    java.time.format.TextStyle.SHORT,
                    java.util.Locale.getDefault())
                    + "  ·  UTC" + now.getOffset();

            Platform.runLater(() -> {
                clockLabel.setText(time);
                dateLabel.setText(date);
                tzLabel.setText(tz);
            });
        }, 0, 1, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (scheduler != null) scheduler.shutdownNow();
    }
}