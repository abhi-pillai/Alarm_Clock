package org.example;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalTime;
import java.util.Optional;

public class AlarmController {

    // ── Picker ──────────────────────────────────────────────────────
    @FXML private Label        hourDisplay;
    @FXML private Label        minuteDisplay;
    @FXML private ToggleButton amBtn;
    @FXML private ToggleButton pmBtn;
    @FXML private Canvas       dialCanvas;
    @FXML private HBox         drumBox;
    @FXML private ListView<String> hourDrum;
    @FXML private ListView<String> minuteDrum;
    @FXML private Button       modeToggleBtn;

    // ── Options ─────────────────────────────────────────────────────
    @FXML private CheckBox     repeatCheck;
    @FXML private TextField    labelField;
    @FXML private ToggleButton snoozeToggle;
    @FXML private Label        selectedTuneLabel;

    // ── Alarm list ──────────────────────────────────────────────────
    @FXML private ListView<Alarm> alarmListView;
    @FXML private Label           statusLabel;

    // ── State ───────────────────────────────────────────────────────
    private int     selectedHour   = 7;
    private int     selectedMinute = 30;
    private boolean isPm           = false;
    private boolean editingHour    = true;
    private boolean drumMode       = false;

    // selectedTune is always kept in sync — no @FXML ListView needed
    private TuneManager.Tune selectedTune = null;

    // Shared tunes list — passed into the popup by reference
    final ObservableList<TuneManager.Tune> tunes =
            FXCollections.observableArrayList();

    private final ObservableList<Alarm> alarms =
            FXCollections.observableArrayList();

    private AlarmManager alarmManager;

    // ── Initialize ──────────────────────────────────────────────────

    @FXML
    public void initialize() {

        // ── Alarm list ──
        alarms.addAll(PersistenceManager.load());
        alarmListView.setItems(alarms);
        alarms.addListener(
            (javafx.collections.ListChangeListener<Alarm>)
                c -> PersistenceManager.save(alarms));

        alarmManager = new AlarmManager(alarms, this::onAlarmTriggered);
        alarmManager.start();

        // ── Tune list ──
        // Load tunes into the shared list — no ListView here
        tunes.setAll(TuneManager.loadAll());

        // Set default selected tune directly
        if (!tunes.isEmpty()) selectTune(tunes.get(0));

        // ── AM/PM ──
        amBtn.setSelected(true);
        pmBtn.setSelected(false);

        // ── Drum lists ──
        ObservableList<String> hours =
                FXCollections.observableArrayList();
        for (int i = 1; i <= 12; i++)
            hours.add(String.format("%02d", i));
        hourDrum.setItems(hours);

        ObservableList<String> minutes =
                FXCollections.observableArrayList();
        for (int i = 0; i < 60; i++)
            minutes.add(String.format("%02d", i));
        minuteDrum.setItems(minutes);

        // Sync drum → state
        hourDrum.getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, o, n) -> {
                if (n != null) {
                    selectedHour = Integer.parseInt(n);
                    refreshDisplay();
                }
            });

        minuteDrum.getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, o, n) -> {
                if (n != null) {
                    selectedMinute = Integer.parseInt(n);
                    refreshDisplay();
                }
            });

        refreshDisplay();
        drawDial();
    }

    // ── Tune selection ──────────────────────────────────────────────

    // Single method — always call this to update selectedTune
    private void selectTune(TuneManager.Tune tune) {
        selectedTune = tune;
        if (selectedTuneLabel != null)
            selectedTuneLabel.setText(tune.getName());
    }

    // ── Sound picker popup ──────────────────────────────────────────

    @FXML
    private void handleOpenSoundPicker() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/org/example/sound_picker.fxml"));
            Parent root = loader.load();

            SoundPickerController controller =
                    loader.getController();

            // Pass shared tunes list + current selection into popup
            controller.init(tunes, selectedTune);

            Stage popupStage = new Stage();
            popupStage.setTitle("Choose sound");
            popupStage.setScene(new Scene(root));
            popupStage.setResizable(false);

            // Modal — blocks main window until closed
            popupStage.initModality(Modality.WINDOW_MODAL);
            popupStage.initOwner(
                    selectedTuneLabel.getScene().getWindow());

            // On close — stop audio + read back selection
            popupStage.setOnHidden(e -> {
                SoundEngine.stopCurrent();
                TuneManager.Tune picked =
                        controller.getSelectedTune();
                if (picked != null) selectTune(picked);
            });

            popupStage.showAndWait();

        } catch (Exception e) {
            System.err.println(
                    "Failed to open sound picker: "
                    + e.getMessage());
            e.printStackTrace();
        }
    }

    // ── OK / Cancel ─────────────────────────────────────────────────

    @FXML
    private void handleOk() {
        // Read tune ID directly from selectedTune — always in sync
        String tuneId = (selectedTune != null)
                ? selectedTune.getId()
                : TuneManager.Tune.DEFAULT_ID;

        int       hour24 = selectedHour % 12 + (isPm ? 12 : 0);
        LocalTime time   = LocalTime.of(hour24, selectedMinute);
        String    label  = labelField.getText().trim();
        boolean   repeat = repeatCheck.isSelected();

        alarms.add(new Alarm(time, label, repeat, tuneId));

        String tuneName = selectedTune != null
                ? selectedTune.getName() : "Default beep";
        statusLabel.setText("Alarm set · "
                + String.format("%02d:%02d", hour24, selectedMinute)
                + " · " + tuneName);

        // Reset fields
        labelField.clear();
        repeatCheck.setSelected(false);
    }

    @FXML
    private void handleCancel() {
        selectedHour   = 7;
        selectedMinute = 30;
        isPm           = false;
        editingHour    = true;

        amBtn.setSelected(true);
        pmBtn.setSelected(false);
        labelField.clear();
        repeatCheck.setSelected(false);

        // Reset tune to default
        if (!tunes.isEmpty()) selectTune(tunes.get(0));

        statusLabel.setText("");
        refreshDisplay();
    }

    // ── Alarm trigger ────────────────────────────────────────────────

    private void onAlarmTriggered(Alarm alarm) {
        String tuneId = alarm.getTuneId();

        TuneManager.Tune tuneToPlay = tunes.stream()
                .filter(t -> t.getId().equals(tuneId))
                .findFirst()
                .orElseGet(() -> tunes.isEmpty()
                        ? null : tunes.get(0));

        System.out.println("Alarm firing: "
                + alarm.getLabel()
                + " | tuneId: " + tuneId
                + " | playing: "
                + (tuneToPlay != null
                        ? tuneToPlay.getName() : "null"));

        SoundEngine.play(tuneToPlay);

        String displayLabel = alarm.getLabel().isBlank()
                ? "Alarm at " + alarm.getTime()
                : alarm.getLabel();

        statusLabel.setText("Firing: " + displayLabel);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Alarm");
        alert.setHeaderText(displayLabel);
        alert.setContentText("What would you like to do?");

        ButtonType snoozeBtn  = new ButtonType("Snooze 5 min");
        ButtonType dismissBtn = new ButtonType("Dismiss",
                ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(snoozeBtn, dismissBtn);

        Optional<ButtonType> result = alert.showAndWait();

        // Stop sound immediately on any button press
        SoundEngine.stopCurrent();

        if (result.isPresent() && result.get() == snoozeBtn) {
            alarmManager.snooze(alarm);
            statusLabel.setText("Snoozed 5 min");
        } else {
            statusLabel.setText("Dismissed · " + displayLabel);
        }
    }

    // ── Alarm list ───────────────────────────────────────────────────

    @FXML
    private void handleDeleteAlarm() {
        Alarm sel = alarmListView
                .getSelectionModel().getSelectedItem();
        if (sel != null) alarms.remove(sel);
    }

    // ── Snooze toggle ────────────────────────────────────────────────

    @FXML
    private void handleSnoozeToggle() {
        snoozeToggle.getStyleClass().removeAll("ios-toggle-on");
        if (snoozeToggle.isSelected())
            snoozeToggle.getStyleClass().add("ios-toggle-on");
    }

    // ── Picker display ────────────────────────────────────────────────

    private void refreshDisplay() {
        hourDisplay.setText(
                String.format("%02d", selectedHour));
        minuteDisplay.setText(
                String.format("%02d", selectedMinute));

        hourDisplay.getStyleClass()
                .removeAll("time-display-active");
        minuteDisplay.getStyleClass()
                .removeAll("time-display-active");

        if (editingHour)
            hourDisplay.getStyleClass()
                    .add("time-display-active");
        else
            minuteDisplay.getStyleClass()
                    .add("time-display-active");

        drawDial();
    }

    @FXML
    private void handleHourClick() {
        editingHour = true;
        refreshDisplay();
    }

    @FXML
    private void handleMinuteClick() {
        editingHour = false;
        refreshDisplay();
    }

    @FXML
    private void handleAmPm() {
        isPm = pmBtn.isSelected();
        amBtn.setSelected(!isPm);
        pmBtn.setSelected(isPm);
    }

    @FXML
    private void handleModeToggle() {
        drumMode = !drumMode;
        dialCanvas.setVisible(!drumMode);
        dialCanvas.setManaged(!drumMode);
        drumBox.setVisible(drumMode);
        drumBox.setManaged(drumMode);
        modeToggleBtn.setText(drumMode ? "🕐" : "⌨");

        if (drumMode) {
            hourDrum.getSelectionModel()
                    .select(selectedHour - 1);
            minuteDrum.getSelectionModel()
                    .select(selectedMinute);
            hourDrum.scrollTo(
                    Math.max(0, selectedHour - 2));
            minuteDrum.scrollTo(
                    Math.max(0, selectedMinute - 1));
        }
    }

    // ── Dial drawing ──────────────────────────────────────────────────

    private void drawDial() {
        GraphicsContext gc =
                dialCanvas.getGraphicsContext2D();
        double w  = dialCanvas.getWidth();
        double h  = dialCanvas.getHeight();
        double cx = w / 2, cy = h / 2;
        double r  = Math.min(w, h) / 2 - 4;

        gc.clearRect(0, 0, w, h);

        // Face
        gc.setFill(Color.web("#f0f5f3"));
        gc.fillOval(cx - r, cy - r, r * 2, r * 2);

        // Numbers
        int count = editingHour ? 12 : 60;
        int step  = editingHour ? 1  : 5;
        gc.setFont(Font.font(
                "Segoe UI", FontWeight.NORMAL, r * 0.13));
        gc.setTextAlign(TextAlignment.CENTER);

        for (int i = step; i <= count; i += step) {
            double angle = Math.toRadians(
                    i * (360.0 / count) - 90);
            double nr = r * 0.78;
            double nx = cx + nr * Math.cos(angle);
            double ny = cy + nr * Math.sin(angle)
                    + r * 0.05;
            String lbl = editingHour
                    ? String.valueOf(i)
                    : String.format("%02d",
                            i == 60 ? 0 : i);
            gc.setFill(Color.web("#5a7a72"));
            gc.fillText(lbl, nx, ny);
        }

        // Hand angle
        double handAngle = editingHour
                ? Math.toRadians(selectedHour * 30 - 90)
                : Math.toRadians(selectedMinute * 6 - 90);

        double handR = r * 0.62;
        gc.setStroke(Color.web("#4a7c6f"));
        gc.setLineWidth(2);
        gc.strokeLine(cx, cy,
                cx + handR * Math.cos(handAngle),
                cy + handR * Math.sin(handAngle));

        // Selected dot
        double dotR  = r * 0.13;
        double dotRv = r * 0.78;
        double dx = cx + dotRv * Math.cos(handAngle);
        double dy = cy + dotRv * Math.sin(handAngle);
        gc.setFill(Color.web("#4a7c6f"));
        gc.fillOval(dx - dotR, dy - dotR,
                dotR * 2, dotR * 2);

        // Label on dot
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(
                "Segoe UI", FontWeight.BOLD, r * 0.13));
        String selLbl = editingHour
                ? String.valueOf(selectedHour)
                : String.format("%02d", selectedMinute);
        gc.fillText(selLbl, dx, dy + r * 0.05);

        // Center pivot
        gc.setFill(Color.web("#4a7c6f"));
        gc.fillOval(cx - 5, cy - 5, 10, 10);
        gc.setFill(Color.WHITE);
        gc.fillOval(cx - 2.5, cy - 2.5, 5, 5);
    }

    @FXML
    private void handleDialPress(MouseEvent e) {
        updateFromDial(e);
    }

    @FXML
    private void handleDialDrag(MouseEvent e) {
        updateFromDial(e);
    }

    private void updateFromDial(MouseEvent e) {
        double cx = dialCanvas.getWidth()  / 2;
        double cy = dialCanvas.getHeight() / 2;
        double angle = Math.toDegrees(
                Math.atan2(e.getY() - cy,
                           e.getX() - cx)) + 90;
        if (angle < 0) angle += 360;

        if (editingHour) {
            int h = (int) Math.round(angle / 30);
            if (h == 0) h = 12;
            if (h > 12) h = 12;
            selectedHour = h;
            // Auto-switch to minute after pressing hour
            if (e.getEventType()
                    == MouseEvent.MOUSE_PRESSED)
                editingHour = false;
        } else {
            selectedMinute =
                    (int) Math.round(angle / 6) % 60;
        }

        refreshDisplay();
    }

    public void shutdown() {
        if (alarmManager != null) alarmManager.stop();
    }
}