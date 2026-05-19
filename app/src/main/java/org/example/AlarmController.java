package org.example;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalTime;
// import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class AlarmController {

    @FXML private Spinner<Integer>    hourSpinner;
    @FXML private Spinner<Integer>    minuteSpinner;
    @FXML private TextField           labelField;
    @FXML private CheckBox            repeatCheck;
    @FXML private ListView<Alarm>     alarmListView;
    @FXML private Label               statusLabel;

    private final ObservableList<Alarm> alarms =
            FXCollections.observableArrayList();

    private AlarmManager alarmManager;

    @FXML
    public void initialize() {
        setupSpinners();

        alarms.addAll(PersistenceManager.load());
        alarmListView.setItems(alarms);

        alarmManager = new AlarmManager(alarms, this::onAlarmTriggered);
        alarmManager.start();

        alarms.addListener(
            (javafx.collections.ListChangeListener<Alarm>)
                change -> PersistenceManager.save(alarms));
    }

    private void setupSpinners() {
        hourSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 7) {
                @Override public void increment(int s) { setValue((getValue()+s)%24); }
                @Override public void decrement(int s) { setValue((getValue()-s+24)%24); }
            });
        minuteSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 30) {
                @Override public void increment(int s) { setValue((getValue()+s)%60); }
                @Override public void decrement(int s) { setValue((getValue()-s+60)%60); }
            });
        hourSpinner.getValueFactory().setConverter(new TwoDigitConverter(24));
        minuteSpinner.getValueFactory().setConverter(new TwoDigitConverter(60));
    }

    private void onAlarmTriggered(Alarm alarm) {
        SoundEngine.playAlarm();
        statusLabel.setText("Alarm fired: " + alarm);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Alarm!");
        alert.setHeaderText(alarm.getLabel().isBlank()
                ? "Alarm at " + alarm.getTime() : alarm.getLabel());
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
        hourSpinner.commitValue();
        minuteSpinner.commitValue();

        LocalTime time    = LocalTime.of(hourSpinner.getValue(),
                                         minuteSpinner.getValue());
        String    label   = labelField.getText().trim();
        boolean   repeat  = repeatCheck.isSelected();

        alarms.add(new Alarm(time, label, repeat));
        labelField.clear();
        repeatCheck.setSelected(false);
    }

    @FXML
    private void handleDeleteAlarm() {
        Alarm selected = alarmListView.getSelectionModel().getSelectedItem();
        if (selected != null) alarms.remove(selected);
    }

    public void shutdown() {
        if (alarmManager != null) alarmManager.stop();
    }
}