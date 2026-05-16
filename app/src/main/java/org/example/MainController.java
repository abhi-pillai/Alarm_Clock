package org.example;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainController {

    @FXML private Label               clockLabel;
    @FXML private Spinner<Integer>    hourSpinner;
    @FXML private Spinner<Integer>    minuteSpinner;
    @FXML private TextField           labelField;
    @FXML private CheckBox            repeatCheck;
    @FXML private ListView<Alarm>     alarmListView;
    @FXML private Label               statusLabel;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ObservableList<Alarm> alarms =
            FXCollections.observableArrayList();

    private ScheduledExecutorService clockScheduler;
    private AlarmManager             alarmManager;

    @FXML
    public void initialize() {
        // Make the spinners wrap around:
        // 23 → 0 and 0 → 23 for hours; 59 → 0 for minutes
        hourSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 7) {
                @Override public void increment(int steps) {
                    setValue((getValue() + steps) % 24);
                }
                @Override public void decrement(int steps) {
                    setValue((getValue() - steps + 24) % 24);
                }
            }
        );

        minuteSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 30) {
                @Override public void increment(int steps) {
                    setValue((getValue() + steps) % 60);
                }
                @Override public void decrement(int steps) {
                    setValue((getValue() - steps + 60) % 60);
                }
            }
        );

        // Format display as two digits: "07" not "7"
        hourSpinner.getValueFactory().setConverter(new TwoDigitConverter(24));
        minuteSpinner.getValueFactory().setConverter(new TwoDigitConverter(60));

        alarms.addAll(PersistenceManager.load());
        alarmListView.setItems(alarms);

        alarmManager = new AlarmManager(alarms, this::onAlarmTriggered);
        alarmManager.start();
        startClock();

        alarms.addListener((javafx.collections.ListChangeListener<Alarm>)
                change -> PersistenceManager.save(alarms));
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

    public void shutdown() {
        if (clockScheduler != null) clockScheduler.shutdownNow();
        if (alarmManager   != null) alarmManager.stop();
    }

    private void onAlarmTriggered(Alarm alarm) {
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
        // Commit any manually typed value before reading
        hourSpinner.commitValue();
        minuteSpinner.commitValue();

        int     hour      = hourSpinner.getValue();
        int     minute    = minuteSpinner.getValue();
        String  labelInput = labelField.getText().trim();
        boolean isRepeat  = repeatCheck.isSelected();

        LocalTime alarmTime = LocalTime.of(hour, minute);
        alarms.add(new Alarm(alarmTime, labelInput, isRepeat));

        labelField.clear();
        repeatCheck.setSelected(false);
    }

    @FXML
    private void handleDeleteAlarm() {
        Alarm selected = alarmListView.getSelectionModel().getSelectedItem();
        if (selected != null) alarms.remove(selected);
    }
}