package org.example;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.time.LocalTime;

public class AlarmSetterController {

    // ── Picker ──
    @FXML private Label        hourDisplay;
    @FXML private Label        minuteDisplay;
    @FXML private ToggleButton amBtn;
    @FXML private ToggleButton pmBtn;
    @FXML private Canvas       dialCanvas;
    @FXML private HBox         drumBox;
    @FXML private ListView<String> hourDrum;
    @FXML private ListView<String> minuteDrum;
    @FXML private Button       modeToggleBtn;

    // ── Options ──
    @FXML private CheckBox     repeatCheck;
    @FXML private TextField    labelField;
    @FXML private ToggleButton snoozeToggle;
    @FXML private Label        selectedTuneLabel;

    // ── State ──
    private int     selectedHour   = 7;
    private int     selectedMinute = 30;
    private boolean isPm           = false;
    private boolean editingHour    = true;
    private boolean drumMode       = false;

    private TuneManager.Tune                       selectedTune = null;
    private ObservableList<TuneManager.Tune>       tunes;

    // The alarm result — null if cancelled
    private Alarm result = null;

    // ─── Init ────────────────────────────────────────────────────────

    // Called by AlarmController after loading FXML
    // Pass tunes list and optionally an existing alarm to pre-fill (edit mode)
    public void init(ObservableList<TuneManager.Tune> tunes,
                     Alarm existingAlarm) {
        this.tunes = tunes;

        // Default tune
        selectedTune = tunes.isEmpty() ? null : tunes.get(0);
        selectedTuneLabel.setText(
                selectedTune != null ? selectedTune.getName() : "Default beep");

        // Pre-fill if editing an existing alarm
        if (existingAlarm != null) {
            int hour24 = existingAlarm.getTime().getHour();
            selectedHour   = hour24 % 12 == 0 ? 12 : hour24 % 12;
            selectedMinute = existingAlarm.getTime().getMinute();
            isPm           = hour24 >= 12;

            labelField.setText(existingAlarm.getLabel());
            repeatCheck.setSelected(existingAlarm.isRepeat());

            // Find and set the tune
            tunes.stream()
                .filter(t -> t.getId().equals(existingAlarm.getTuneId()))
                .findFirst()
                .ifPresent(t -> {
                    selectedTune = t;
                    selectedTuneLabel.setText(t.getName());
                });
        }

        amBtn.setSelected(!isPm);
        pmBtn.setSelected(isPm);

        // Drum lists
        ObservableList<String> hours = FXCollections.observableArrayList();
        for (int i = 1; i <= 12; i++) hours.add(String.format("%02d", i));
        hourDrum.setItems(hours);

        ObservableList<String> minutes = FXCollections.observableArrayList();
        for (int i = 0; i < 60; i++) minutes.add(String.format("%02d", i));
        minuteDrum.setItems(minutes);

        hourDrum.getSelectionModel().selectedItemProperty()
            .addListener((obs, o, n) -> {
                if (n != null) { selectedHour = Integer.parseInt(n); refreshDisplay(); }
            });
        minuteDrum.getSelectionModel().selectedItemProperty()
            .addListener((obs, o, n) -> {
                if (n != null) { selectedMinute = Integer.parseInt(n); refreshDisplay(); }
            });

        refreshDisplay();
        drawDial();
    }

    // Returns the built Alarm, or null if cancelled
    public Alarm getResult() { return result; }

    // ─── Sound picker ─────────────────────────────────────────────────

    @FXML
    private void handleOpenSoundPicker() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/org/example/sound_picker.fxml"));
            javafx.scene.Parent root = loader.load();
            SoundPickerController ctrl = loader.getController();
            ctrl.init(tunes, selectedTune);

            Stage stage = new Stage();
            stage.setTitle("Choose sound");
            stage.setScene(new javafx.scene.Scene(root));
            stage.setResizable(false);
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            stage.initOwner(dialCanvas.getScene().getWindow());
            stage.setOnHidden(e -> {
                SoundEngine.stopCurrent();
                TuneManager.Tune picked = ctrl.getSelectedTune();
                if (picked != null) {
                    selectedTune = picked;
                    selectedTuneLabel.setText(picked.getName());
                }
            });
            stage.showAndWait();
        } catch (Exception e) {
            System.err.println("Failed to open sound picker: " + e.getMessage());
        }
    }

    // ─── Save / Cancel ────────────────────────────────────────────────

    @FXML
    private void handleSave() {
        String tuneId = selectedTune != null
                ? selectedTune.getId()
                : TuneManager.Tune.DEFAULT_ID;

        int       hour24 = selectedHour % 12 + (isPm ? 12 : 0);
        LocalTime time   = LocalTime.of(hour24, selectedMinute);
        String    label  = labelField.getText().trim();
        boolean   repeat = repeatCheck.isSelected();

        result = new Alarm(time, label, repeat, tuneId);

        Stage stage = (Stage) labelField.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void handleCancel() {
        result = null;
        SoundEngine.stopCurrent();
        Stage stage = (Stage) labelField.getScene().getWindow();
        stage.close();
    }

    // ─── Snooze toggle ────────────────────────────────────────────────

    @FXML
    private void handleSnoozeToggle() {
        snoozeToggle.getStyleClass().removeAll("ios-toggle-on");
        if (snoozeToggle.isSelected())
            snoozeToggle.getStyleClass().add("ios-toggle-on");
    }

    // ─── Display ─────────────────────────────────────────────────────

    private void refreshDisplay() {
        hourDisplay.setText(String.format("%02d", selectedHour));
        minuteDisplay.setText(String.format("%02d", selectedMinute));

        hourDisplay.getStyleClass().removeAll("time-display-active");
        minuteDisplay.getStyleClass().removeAll("time-display-active");
        if (editingHour)
            hourDisplay.getStyleClass().add("time-display-active");
        else
            minuteDisplay.getStyleClass().add("time-display-active");

        drawDial();
    }

    @FXML private void handleHourClick()   { editingHour = true;  refreshDisplay(); }
    @FXML private void handleMinuteClick() { editingHour = false; refreshDisplay(); }

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
            hourDrum.getSelectionModel().select(selectedHour - 1);
            minuteDrum.getSelectionModel().select(selectedMinute);
            hourDrum.scrollTo(Math.max(0, selectedHour - 2));
            minuteDrum.scrollTo(Math.max(0, selectedMinute - 1));
        }
    }

    // ─── Dial drawing ─────────────────────────────────────────────────

    private void drawDial() {
        GraphicsContext gc = dialCanvas.getGraphicsContext2D();
        double w  = dialCanvas.getWidth();
        double h  = dialCanvas.getHeight();
        double cx = w / 2, cy = h / 2;
        double r  = Math.min(w, h) / 2 - 4;

        gc.clearRect(0, 0, w, h);

        gc.setFill(Color.web("#f0f5f3"));
        gc.fillOval(cx - r, cy - r, r * 2, r * 2);

        int count = editingHour ? 12 : 60;
        int step  = editingHour ? 1  : 5;
        gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, r * 0.13));
        gc.setTextAlign(TextAlignment.CENTER);

        for (int i = step; i <= count; i += step) {
            double angle = Math.toRadians(i * (360.0 / count) - 90);
            double nr    = r * 0.78;
            double nx    = cx + nr * Math.cos(angle);
            double ny    = cy + nr * Math.sin(angle) + r * 0.05;
            String lbl   = editingHour
                    ? String.valueOf(i)
                    : String.format("%02d", i == 60 ? 0 : i);
            gc.setFill(Color.web("#5a7a72"));
            gc.fillText(lbl, nx, ny);
        }

        double handAngle = editingHour
                ? Math.toRadians(selectedHour * 30 - 90)
                : Math.toRadians(selectedMinute * 6 - 90);

        double handR = r * 0.62;
        gc.setStroke(Color.web("#4a7c6f"));
        gc.setLineWidth(2);
        gc.strokeLine(cx, cy,
                cx + handR * Math.cos(handAngle),
                cy + handR * Math.sin(handAngle));

        double dotR  = r * 0.13;
        double dotRv = r * 0.78;
        double dx    = cx + dotRv * Math.cos(handAngle);
        double dy    = cy + dotRv * Math.sin(handAngle);
        gc.setFill(Color.web("#4a7c6f"));
        gc.fillOval(dx - dotR, dy - dotR, dotR * 2, dotR * 2);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, r * 0.13));
        String selLbl = editingHour
                ? String.valueOf(selectedHour)
                : String.format("%02d", selectedMinute);
        gc.fillText(selLbl, dx, dy + r * 0.05);

        gc.setFill(Color.web("#4a7c6f"));
        gc.fillOval(cx - 5, cy - 5, 10, 10);
        gc.setFill(Color.WHITE);
        gc.fillOval(cx - 2.5, cy - 2.5, 5, 5);
    }

    @FXML private void handleDialPress(MouseEvent e) { updateFromDial(e); }
    @FXML private void handleDialDrag(MouseEvent e)  { updateFromDial(e); }

    private void updateFromDial(MouseEvent e) {
        double cx    = dialCanvas.getWidth()  / 2;
        double cy    = dialCanvas.getHeight() / 2;
        double angle = Math.toDegrees(
                Math.atan2(e.getY() - cy, e.getX() - cx)) + 90;
        if (angle < 0) angle += 360;

        if (editingHour) {
            int h = (int) Math.round(angle / 30);
            if (h == 0) h = 12;
            if (h > 12) h = 12;
            selectedHour = h;
            if (e.getEventType() == MouseEvent.MOUSE_PRESSED)
                editingHour = false;
        } else {
            selectedMinute = (int) Math.round(angle / 6) % 60;
        }
        refreshDisplay();
    }
}