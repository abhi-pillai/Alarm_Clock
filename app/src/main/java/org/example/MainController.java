package org.example;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainController {

    @FXML private Label           clockLabel;
    @FXML private TextField       timeField;
    @FXML private TextField       labelField;
    @FXML private ListView<Alarm> alarmListView;
    @FXML private Label           statusLabel;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ObservableList<Alarm> alarms =
            FXCollections.observableArrayList();

    private ScheduledExecutorService clockScheduler;
    private AlarmManager alarmManager;

    @FXML
    public void initialize() {
        // 1. Load saved alarms from disk on startup
        alarms.addAll(PersistenceManager.load());

        alarmListView.setItems(alarms);
        alarmManager = new AlarmManager(alarms, this::onAlarmTriggered);
        alarmManager.start();
        startClock();

        // 2. Auto-save whenever the alarm list changes
        alarms.addListener((javafx.collections.ListChangeListener<Alarm>) change ->
                PersistenceManager.save(alarms));
    }

    private void startClock() {
        clockScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "clock-thread");
            t.setDaemon(true);
            return t;
        });
        clockScheduler.scheduleAtFixedRate(() -> {
            String now = LocalTime.now().format(TIME_FORMAT);
            Platform.runLater(() -> clockLabel.setText(now));
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void onAlarmTriggered(Alarm alarm) {
        // 3. Play sound the moment the alarm fires
        SoundEngine.playAlarm();

        statusLabel.setText("Alarm fired: " + alarm);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Alarm!");
        alert.setHeaderText(alarm.getLabel().isBlank()
                ? "Alarm at " + alarm.getTime()
                : alarm.getLabel());
        alert.setContentText("What would you like to do?");

        ButtonType snoozeBtn  = new ButtonType("Snooze 5 min");
        ButtonType dismissBtn = new ButtonType("Dismiss",
                ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(snoozeBtn, dismissBtn);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == snoozeBtn) {
            alarmManager.snooze(alarm);
            statusLabel.setText("Snoozed for 5 minutes");
        } else {
            statusLabel.setText("Alarm dismissed");
        }
    }

    @FXML
    private void handleAddAlarm() {
        String timeInput  = timeField.getText().trim();
        String labelInput = labelField.getText().trim();

        try {
            LocalTime alarmTime = LocalTime.parse(timeInput,
                    DateTimeFormatter.ofPattern("HH:mm"));
            alarms.add(new Alarm(alarmTime, labelInput));
            timeField.clear();
            labelField.clear();
        } catch (DateTimeParseException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Invalid time");
            alert.setHeaderText(null);
            alert.setContentText("Please enter time in HH:MM format, e.g. 07:30");
            alert.showAndWait();
        }
    }

    @FXML
    private void handleDeleteAlarm() {
        Alarm selected = alarmListView.getSelectionModel().getSelectedItem();
        if (selected != null) alarms.remove(selected);
    }
}