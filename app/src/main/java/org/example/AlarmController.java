package org.example;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.time.LocalTime;
import java.util.Optional;

public class AlarmController {

    // ── Digital display ──
    @FXML private Label        hourDisplay;
    @FXML private Label        minuteDisplay;
    @FXML private ToggleButton amBtn;
    @FXML private ToggleButton pmBtn;

    // ── Picker panels ──
    @FXML private Canvas       dialCanvas;
    @FXML private HBox         drumBox;
    @FXML private ListView<String> hourDrum;
    @FXML private ListView<String> minuteDrum;
    @FXML private Button       modeToggleBtn;

    // ── Options ──
    @FXML private CheckBox     repeatCheck;
    @FXML private TextField    labelField;
    @FXML private ToggleButton snoozeToggle;

    // ── Alarm list ──
    @FXML private ListView<Alarm> alarmListView;
    @FXML private Label           statusLabel;

    // ── State ──
    private int     selectedHour   = 1;   // 1–12
    private int     selectedMinute = 0;
    private boolean isPm           = false;
    private boolean editingHour    = true;  // true = dial shows hours
    private boolean drumMode       = false; // false = dial, true = drum

    private AlarmManager alarmManager;
    private final ObservableList<Alarm> alarms =
            FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Alarm list
        alarms.addAll(PersistenceManager.load());
        alarmListView.setItems(alarms);
        alarms.addListener((javafx.collections.ListChangeListener<Alarm>)
                c -> PersistenceManager.save(alarms));
        alarmManager = new AlarmManager(alarms, this::onAlarmTriggered);
        alarmManager.start();

        // AM selected by default
        amBtn.setSelected(true);

        // Build drum lists
        ObservableList<String> hours = FXCollections.observableArrayList();
        for (int i = 1; i <= 12; i++) hours.add(String.format("%02d", i));
        hourDrum.setItems(hours);
        hourDrum.getSelectionModel().select(0); // 01

        ObservableList<String> minutes = FXCollections.observableArrayList();
        for (int i = 0; i < 60; i++) minutes.add(String.format("%02d", i));
        minuteDrum.setItems(minutes);
        minuteDrum.getSelectionModel().select(0); // 00

        // Sync drum selection → display
        hourDrum.getSelectionModel().selectedItemProperty().addListener(
            (obs, o, n) -> { if (n != null) {
                selectedHour = Integer.parseInt(n);
                refreshDisplay();
            }});
        minuteDrum.getSelectionModel().selectedItemProperty().addListener(
            (obs, o, n) -> { if (n != null) {
                selectedMinute = Integer.parseInt(n);
                refreshDisplay();
            }});

        refreshDisplay();
        drawDial();
    }

    // ─── Display helpers ─────────────────────────────────────────────

    private void refreshDisplay() {
        hourDisplay.setText(String.format("%02d", selectedHour));
        minuteDisplay.setText(String.format("%02d", selectedMinute));

        // Bold the currently-editing segment
        hourDisplay.getStyleClass().removeAll("time-display-active");
        minuteDisplay.getStyleClass().removeAll("time-display-active");
        if (editingHour)
            hourDisplay.getStyleClass().add("time-display-active");
        else
            minuteDisplay.getStyleClass().add("time-display-active");

        drawDial();
    }

    // Click on hour display → switch dial to hour editing
    @FXML private void handleHourClick()   {
        editingHour = true;  refreshDisplay(); }
    @FXML private void handleMinuteClick() {
        editingHour = false; refreshDisplay(); }

    // ─── AM / PM ─────────────────────────────────────────────────────

    @FXML
    private void handleAmPm() {
        // Whichever button was just clicked becomes selected; deselect the other
        isPm = pmBtn.isSelected();
        amBtn.setSelected(!isPm);
        pmBtn.setSelected(isPm);
    }

    // ─── Mode toggle (dial ↔ drum) ────────────────────────────────────

    @FXML
    private void handleModeToggle() {
        drumMode = !drumMode;
        dialCanvas.setVisible(!drumMode);
        dialCanvas.setManaged(!drumMode);
        drumBox.setVisible(drumMode);
        drumBox.setManaged(drumMode);
        modeToggleBtn.setText(drumMode ? "🕐" : "⌨");

        if (drumMode) {
            // Sync drum to current selection
            hourDrum.getSelectionModel().select(selectedHour - 1);
            minuteDrum.getSelectionModel().select(selectedMinute);
            // Scroll selected item to center
            hourDrum.scrollTo(Math.max(0, selectedHour - 2));
            minuteDrum.scrollTo(Math.max(0, selectedMinute - 1));
        }
    }

    // ─── Dial drawing ────────────────────────────────────────────────

    private void drawDial() {
        GraphicsContext gc = dialCanvas.getGraphicsContext2D();
        double w  = dialCanvas.getWidth();
        double h  = dialCanvas.getHeight();
        double cx = w / 2, cy = h / 2;
        double r  = Math.min(w, h) / 2 - 4;

        gc.clearRect(0, 0, w, h);

        // Face
        gc.setFill(Color.web("#ede7f6"));
        gc.fillOval(cx - r, cy - r, r * 2, r * 2);

        // Numbers
        int count = editingHour ? 12 : 60;
        int step  = editingHour ? 1  : 5;

        gc.setFont(Font.font("System", FontWeight.NORMAL, r * 0.13));
        gc.setTextAlign(TextAlignment.CENTER);

        for (int i = step; i <= count; i += step) {
            double angle = Math.toRadians(i * (360.0 / count) - 90);
            double nr    = r * 0.78;
            double nx    = cx + nr * Math.cos(angle);
            double ny    = cy + nr * Math.sin(angle) + r * 0.05;

            String label = editingHour
                    ? String.valueOf(i)
                    : String.format("%02d", i == 60 ? 0 : i);

            gc.setFill(Color.web("#7c6fa0"));
            gc.fillText(label, nx, ny);
        }

        // Compute hand angle for selected value
        double handAngle;
        if (editingHour) {
            handAngle = Math.toRadians(selectedHour * 30 - 90);
        } else {
            handAngle = Math.toRadians(selectedMinute * 6 - 90);
        }

        // Hand line
        double handR = r * 0.62;
        double hx    = cx + handR * Math.cos(handAngle);
        double hy    = cy + handR * Math.sin(handAngle);

        gc.setStroke(Color.web("#7c3aed"));
        gc.setLineWidth(2);
        gc.strokeLine(cx, cy, hx, hy);

        // Selected number circle
        double dotR  = r * 0.13;
        double dotRv = r * 0.78;
        double dx    = cx + dotRv * Math.cos(handAngle);
        double dy    = cy + dotRv * Math.sin(handAngle);
        gc.setFill(Color.web("#7c3aed"));
        gc.fillOval(dx - dotR, dy - dotR, dotR * 2, dotR * 2);

        // Draw selected number again in white on top of purple dot
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("System", FontWeight.BOLD, r * 0.13));
        String selLabel = editingHour
                ? String.valueOf(selectedHour)
                : String.format("%02d", selectedMinute);
        gc.fillText(selLabel, dx, dy + r * 0.05);

        // Center dot
        gc.setFill(Color.web("#7c3aed"));
        gc.fillOval(cx - 5, cy - 5, 10, 10);
    }

    // ─── Dial interaction ─────────────────────────────────────────────

    @FXML
    private void handleDialPress(MouseEvent e) { updateFromDial(e); }

    @FXML
    private void handleDialDrag(MouseEvent e)  { updateFromDial(e); }

    private void updateFromDial(MouseEvent e) {
        double cx    = dialCanvas.getWidth()  / 2;
        double cy    = dialCanvas.getHeight() / 2;
        double dx    = e.getX() - cx;
        double dy    = e.getY() - cy;
        double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90;
        if (angle < 0) angle += 360;

        if (editingHour) {
            // Map 0–360° → 1–12
            int h = (int) Math.round(angle / 30);
            if (h == 0) h = 12;
            if (h > 12) h = 12;
            selectedHour = h;
        } else {
            // Map 0–360° → 0–59
            int m = (int) Math.round(angle / 6) % 60;
            selectedMinute = m;
        }

        // After picking hour, auto-switch to minute editing
        if (editingHour && e.getEventType() == MouseEvent.MOUSE_PRESSED) {
            editingHour = false;
        }

        refreshDisplay();
    }

    // ─── Snooze toggle ───────────────────────────────────────────────

    @FXML
    private void handleSnoozeToggle() {
        // Visual only — actual snooze is handled in onAlarmTriggered
        snoozeToggle.getStyleClass().removeAll("ios-toggle-on");
        if (snoozeToggle.isSelected())
            snoozeToggle.getStyleClass().add("ios-toggle-on");
    }

    // ─── OK / Cancel ─────────────────────────────────────────────────

    @FXML
    private void handleOk() {
        // Convert 12hr → 24hr for LocalTime
        int hour24 = selectedHour % 12 + (isPm ? 12 : 0);
        LocalTime time   = LocalTime.of(hour24, selectedMinute);
        String    label  = labelField.getText().trim();
        boolean   repeat = repeatCheck.isSelected();

        alarms.add(new Alarm(time, label, repeat));
        labelField.clear();
        repeatCheck.setSelected(false);
        statusLabel.setText("Alarm set for "
                + String.format("%02d:%02d", hour24, selectedMinute));
    }

    @FXML
    private void handleCancel() {
        // Reset picker to default
        selectedHour   = 1;
        selectedMinute = 0;
        isPm           = false;
        editingHour    = true;
        amBtn.setSelected(true);
        pmBtn.setSelected(false);
        labelField.clear();
        repeatCheck.setSelected(false);
        refreshDisplay();
        statusLabel.setText("");
    }

    // ─── Alarm list ──────────────────────────────────────────────────

    @FXML
    private void handleDeleteAlarm() {
        Alarm sel = alarmListView.getSelectionModel().getSelectedItem();
        if (sel != null) alarms.remove(sel);
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

    public void shutdown() {
        if (alarmManager != null) alarmManager.stop();
    }
}