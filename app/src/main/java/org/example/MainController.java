package org.example;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.control.Alert;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainController {

    @FXML private Label    clockLabel;
    @FXML private TextField timeField;
    @FXML private TextField labelField;
    @FXML private ListView<Alarm> alarmListView;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    // ObservableList is a special JavaFX list —
    // the ListView updates automatically when items are added/removed
    private final ObservableList<Alarm> alarms =
            FXCollections.observableArrayList();

    private ScheduledExecutorService scheduler;

    @FXML
    public void initialize() {
        // Wire the list to the UI
        alarmListView.setItems(alarms);
        startClock();
    }

    private void startClock() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "clock-thread");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            String now = LocalTime.now().format(TIME_FORMAT);
            Platform.runLater(() -> clockLabel.setText(now));
        }, 0, 1, TimeUnit.SECONDS);
    }

    // Called when user clicks "Add Alarm"
    @FXML
    private void handleAddAlarm() {
        String timeInput  = timeField.getText().trim();
        String labelInput = labelField.getText().trim();

        // Validate the time format
        try {
            LocalTime alarmTime = LocalTime.parse(timeInput,
                    DateTimeFormatter.ofPattern("HH:mm"));

            alarms.add(new Alarm(alarmTime, labelInput));

            // Clear the input fields after adding
            timeField.clear();
            labelField.clear();

        } catch (DateTimeParseException e) {
            // Show a friendly error popup
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Invalid time");
            alert.setHeaderText(null);
            alert.setContentText("Please enter time in HH:MM format, e.g. 07:30");
            alert.showAndWait();
        }
    }

    // Called when user clicks "Delete Selected"
    @FXML
    private void handleDeleteAlarm() {
        Alarm selected = alarmListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            alarms.remove(selected);
        }
    }
}