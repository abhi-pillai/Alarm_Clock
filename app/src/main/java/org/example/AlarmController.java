package org.example;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Optional;

public class AlarmController {

    @FXML private ListView<Alarm> alarmListView;
    @FXML private Label           statusLabel;

    private final ObservableList<Alarm> alarms =
            FXCollections.observableArrayList();
    private final ObservableList<TuneManager.Tune> tunes =
            FXCollections.observableArrayList();

    private AlarmManager alarmManager;

    // ─── Init ────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        alarms.addAll(PersistenceManager.load());
        tunes.setAll(TuneManager.loadAll());

        alarmListView.setItems(alarms);
        alarmListView.setCellFactory(lv -> new AlarmCell());

        alarms.addListener(
            (javafx.collections.ListChangeListener<Alarm>)
                c -> PersistenceManager.save(alarms));

        alarmManager = new AlarmManager(alarms, this::onAlarmTriggered);
        alarmManager.start();
    }

    // ─── Add alarm (FAB button) ───────────────────────────────────────

    @FXML
    private void handleAddAlarm() {
        openSetterWindow(null);
    }

    // Opens the setter window — pass an existing alarm to pre-fill (reapply)
    private void openSetterWindow(Alarm prefill) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/alarm_setter.fxml"));
            Parent root = loader.load();
            AlarmSetterController ctrl = loader.getController();
            ctrl.init(tunes, prefill);

            Stage stage = new Stage();
            stage.setTitle(prefill == null ? "New alarm" : "Edit alarm");
            stage.setScene(new javafx.scene.Scene(root));
            stage.setResizable(false);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(alarmListView.getScene().getWindow());

            stage.setOnHidden(e -> {
                Alarm result = ctrl.getResult();
                if (result != null) {
                    alarms.add(result);
                    statusLabel.setText("Alarm saved · "
                            + result.getTime().toString());
                }
            });

            stage.showAndWait();

        } catch (Exception e) {
            System.err.println("Failed to open alarm setter: "
                    + e.getMessage());
            e.printStackTrace();
        }
    }

    // ─── Alarm trigger ────────────────────────────────────────────────

    private void onAlarmTriggered(Alarm alarm) {
        String tuneId = alarm.getTuneId();

        TuneManager.Tune tuneToPlay = tunes.stream()
                .filter(t -> t.getId().equals(tuneId))
                .findFirst()
                .orElseGet(() -> tunes.isEmpty() ? null : tunes.get(0));

        SoundEngine.play(tuneToPlay);

        String label = alarm.getLabel().isBlank()
                ? "Alarm at " + alarm.getTime() : alarm.getLabel();
        statusLabel.setText("Firing: " + label);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Alarm");
        alert.setHeaderText(label);
        alert.setContentText("What would you like to do?");

        ButtonType snoozeBtn  = new ButtonType("Snooze 5 min");
        ButtonType dismissBtn = new ButtonType("Dismiss",
                ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(snoozeBtn, dismissBtn);

        Optional<ButtonType> result = alert.showAndWait();
        SoundEngine.stopCurrent();

        if (result.isPresent() && result.get() == snoozeBtn) {
            alarmManager.snooze(alarm);
            statusLabel.setText("Snoozed · " + label);
        } else {
            statusLabel.setText("Dismissed · " + label);
        }
    }

    public void shutdown() {
        if (alarmManager != null) alarmManager.stop();
    }

    // ─── Custom alarm list cell ───────────────────────────────────────

    private class AlarmCell extends ListCell<Alarm> {

        private final VBox  card         = new VBox(6);
        private final HBox  topRow       = new HBox(8);
        private final Label timeLabel    = new Label();
        private final Label periodLabel  = new Label();
        private final ToggleButton toggle = new ToggleButton();
        private final HBox  metaRow      = new HBox(6);
        private final Label alarmLabel   = new Label();
        private final Label repeatBadge  = new Label();
        private final Label soundBadge   = new Label();
        private final HBox  actionRow    = new HBox(8);
        private final Button reapplyBtn  = new Button("↺  Reapply");
        private final Button deleteBtn   = new Button("Delete");

        // Track if alarm is "on" (not yet triggered once)
        private boolean alarmOn = true;

        AlarmCell() {
            // ── Structure ──
            topRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            HBox timeBox = new HBox(4);
            timeBox.setAlignment(javafx.geometry.Pos.BASELINE_LEFT);
            timeBox.getChildren().addAll(timeLabel, periodLabel);

            javafx.scene.layout.Region spacer =
                    new javafx.scene.layout.Region();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
            topRow.getChildren().addAll(timeBox, spacer, toggle);

            metaRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            metaRow.getChildren().addAll(
                    alarmLabel, repeatBadge, soundBadge);

            javafx.scene.layout.Region spacer2 =
                    new javafx.scene.layout.Region();
            HBox.setHgrow(spacer2, javafx.scene.layout.Priority.ALWAYS);
            actionRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            actionRow.getChildren().addAll(
                    reapplyBtn, spacer2, deleteBtn);

            // Top separator line
            javafx.scene.control.Separator sep =
                    new javafx.scene.control.Separator();
            sep.getStyleClass().add("option-sep");

            card.getChildren().addAll(topRow, metaRow, sep, actionRow);
            card.getStyleClass().add("alarm-card");

            // ── Styles ──
            timeLabel.getStyleClass().add("alarm-card-time");
            periodLabel.getStyleClass().add("alarm-card-period");
            toggle.getStyleClass().add("ios-toggle");
            alarmLabel.getStyleClass().add("alarm-card-meta");
            repeatBadge.getStyleClass().add("alarm-badge");
            soundBadge.getStyleClass().add("alarm-badge");
            reapplyBtn.getStyleClass().add("reapply-btn");
            deleteBtn.getStyleClass().add("card-delete-btn");

            // ── Toggle on/off ──
            toggle.setOnAction(e -> {
                alarmOn = toggle.isSelected();
                toggle.getStyleClass().removeAll("ios-toggle-on");
                if (alarmOn) toggle.getStyleClass().add("ios-toggle-on");
                // Dimming handled by opacity
                card.setOpacity(alarmOn ? 1.0 : 0.5);
            });

            // ── Reapply ──
            reapplyBtn.setOnAction(e -> {
                Alarm alarm = getItem();
                if (alarm != null) openSetterWindow(alarm);
            });

            // ── Delete ──
            deleteBtn.setOnAction(e -> {
                Alarm alarm = getItem();
                if (alarm != null) {
                    alarms.remove(alarm);
                    statusLabel.setText("Alarm deleted");
                }
            });

            // ── Click card to edit ──
            card.setOnMouseClicked(e -> {
                if (e.getTarget() == toggle
                        || e.getTarget() == reapplyBtn
                        || e.getTarget() == deleteBtn) return;
                Alarm alarm = getItem();
                if (alarm != null) openSetterWindow(alarm);
            });
        }

        @Override
        protected void updateItem(Alarm alarm, boolean empty) {
            super.updateItem(alarm, empty);

            if (empty || alarm == null) {
                setGraphic(null);
                setStyle("-fx-background-color: transparent;");
                return;
            }

            // Format time as 12hr
            int h24  = alarm.getTime().getHour();
            int h12  = h24 % 12 == 0 ? 12 : h24 % 12;
            int min  = alarm.getTime().getMinute();
            String period = h24 >= 12 ? "PM" : "AM";

            timeLabel.setText(String.format("%02d:%02d", h12, min));
            periodLabel.setText(period);

            alarmLabel.setText(
                    alarm.getLabel().isBlank() ? "No label" : alarm.getLabel());

            repeatBadge.setText(alarm.isRepeat() ? "↻ Daily" : "✕ Once");

            // Find tune name
            String tuneName = tunes.stream()
                    .filter(t -> t.getId().equals(alarm.getTuneId()))
                    .map(TuneManager.Tune::getName)
                    .findFirst()
                    .orElse("Default beep");
            soundBadge.setText("♪ " + tuneName);

            // Default toggle state = on
            alarmOn = true;
            toggle.setSelected(true);
            toggle.getStyleClass().removeAll("ios-toggle-on");
            toggle.getStyleClass().add("ios-toggle-on");
            card.setOpacity(1.0);

            setGraphic(card);
            setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        }
    }
}