package org.example;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalTime;
// import java.util.List;
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

    // ── Sound picker ──
    @FXML private Label                      selectedTuneLabel;
    @FXML private VBox                       soundPickerPanel;
    @FXML private ListView<TuneManager.Tune> tuneListView;

    // ── Alarm list ──
    @FXML private ListView<Alarm> alarmListView;
    @FXML private Label           statusLabel;

    // ── State ──
    private int     selectedHour   = 1;
    private int     selectedMinute = 0;
    private boolean isPm           = false;
    private boolean editingHour    = true;
    private boolean drumMode       = false;

    // Currently selected tune (default = null → plays built-in beep)
    private TuneManager.Tune selectedTune = null;

    private final ObservableList<TuneManager.Tune> tunes =
            FXCollections.observableArrayList();

    private AlarmManager alarmManager;
    private final ObservableList<Alarm> alarms =
            FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // ── Alarm list setup ──
        alarms.addAll(PersistenceManager.load());
        alarmListView.setItems(alarms);
        alarms.addListener((javafx.collections.ListChangeListener<Alarm>)
                c -> PersistenceManager.save(alarms));
        alarmManager = new AlarmManager(alarms, this::onAlarmTriggered);
        alarmManager.start();

        // ── Tune list setup ──
        tunes.setAll(TuneManager.loadAll());
        tuneListView.setItems(tunes);
        // Wire list click → selectTune()
        tuneListView.getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, oldVal, newVal) -> {
                if (newVal != null) selectTune(newVal);
            });
        tuneListView.getSelectionModel().selectFirst(); // default beep selected
        selectedTune = tunes.get(0);

        // AM selected by default
        amBtn.setSelected(true);

        // ── Drum lists ──
        ObservableList<String> hours = FXCollections.observableArrayList();
        for (int i = 1; i <= 12; i++) hours.add(String.format("%02d", i));
        hourDrum.setItems(hours);
        hourDrum.getSelectionModel().select(0);

        ObservableList<String> minutes = FXCollections.observableArrayList();
        for (int i = 0; i < 60; i++) minutes.add(String.format("%02d", i));
        minuteDrum.setItems(minutes);
        minuteDrum.getSelectionModel().select(0);

        hourDrum.getSelectionModel().selectedItemProperty().addListener(
            (obs, o, n) -> { if (n != null) {
                selectedHour = Integer.parseInt(n); refreshDisplay(); }});
        minuteDrum.getSelectionModel().selectedItemProperty().addListener(
            (obs, o, n) -> { if (n != null) {
                selectedMinute = Integer.parseInt(n); refreshDisplay(); }});

        refreshDisplay();
        drawDial();
    }

    // ─── Sound picker handlers ────────────────────────────────────────

    // Toggles the sound panel open/closed
    @FXML
    private void handleOpenSoundPicker() {
        boolean show = !soundPickerPanel.isVisible();
        soundPickerPanel.setVisible(show);
        soundPickerPanel.setManaged(show);

        // Sync list selection to current selectedTune
        if (show && selectedTune != null) {
            tuneListView.getSelectionModel().select(selectedTune);
        }
    }

    // Opens a file chooser to import a .wav or .mp3 file
    @FXML
    private void handleAddTune() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select alarm tune");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Audio files", "*.wav", "*.mp3"),
            new FileChooser.ExtensionFilter("WAV files",  "*.wav"),
            new FileChooser.ExtensionFilter("MP3 files",  "*.mp3")
        );

        // Show dialog — getScene().getWindow() gives us the parent window
        File file = chooser.showOpenDialog(
                tuneListView.getScene().getWindow());

        if (file != null) {
            // Use filename without extension as the display name
            String rawName   = file.getName();
            String tuneName  = rawName.contains(".")
                    ? rawName.substring(0, rawName.lastIndexOf('.'))
                    : rawName;

            TuneManager.Tune newTune =
                    new TuneManager.Tune(file.getAbsolutePath(), tuneName);

            // Avoid duplicates
            boolean exists = tunes.stream()
                    .anyMatch(t -> t.getId().equals(newTune.getId()));
            if (!exists) {
                tunes.add(newTune);
                TuneManager.save(tunes); // persist immediately
                tuneListView.getSelectionModel().select(newTune);
                selectTune(newTune);
            }
        }
    }

    // Previews the selected tune without setting an alarm
    @FXML
    private void handlePreviewTune() {
        TuneManager.Tune tune =
                tuneListView.getSelectionModel().getSelectedItem();
        if (tune != null) {
            SoundEngine.play(tune);
        }
    }

    // Removes a user-added tune (can't remove the default)
    @FXML
    private void handleRemoveTune() {
        TuneManager.Tune tune =
                tuneListView.getSelectionModel().getSelectedItem();

        if (tune == null || tune.isDefault()) return;

        tunes.remove(tune);
        TuneManager.save(tunes);

        // Fall back to default if the removed tune was selected
        if (tune.equals(selectedTune)) {
            selectTune(tunes.get(0));
            tuneListView.getSelectionModel().selectFirst();
        }
    }

    // Called when user clicks a tune in the list to select it
    private void selectTune(TuneManager.Tune tune) {
        selectedTune = tune;
        selectedTuneLabel.setText(tune.getName());
    }

    // ─── OK — builds the alarm with the chosen tune ───────────────────

    @FXML
    private void handleOk() {
        // Confirm tune selection from list if panel is open
        TuneManager.Tune listSelection =
                tuneListView.getSelectionModel().getSelectedItem();
        if (listSelection != null) selectTune(listSelection);

        // Close picker panel
        soundPickerPanel.setVisible(false);
        soundPickerPanel.setManaged(false);

        int       hour24 = selectedHour % 12 + (isPm ? 12 : 0);
        LocalTime time   = LocalTime.of(hour24, selectedMinute);
        String    label  = labelField.getText().trim();
        boolean   repeat = repeatCheck.isSelected();

        // Store tune ID inside the label for now using a separator
        // e.g. "Wake up||/home/user/tunes/birds.wav"
        String fullLabel = label;
        if (selectedTune != null && !selectedTune.isDefault()) {
            fullLabel = label + "||" + selectedTune.getId();
        }

        alarms.add(new Alarm(time, fullLabel, repeat));
        labelField.clear();
        repeatCheck.setSelected(false);
        statusLabel.setText("Alarm set for "
                + String.format("%02d:%02d", hour24, selectedMinute)
                + " — " + selectedTune.getName());
    }

    // ─── Alarm trigger — plays the right tune ─────────────────────────

    private void onAlarmTriggered(Alarm alarm) {
        // Extract tune path from label if present
        String    fullLabel = alarm.getLabel();
        String    displayLabel;
        TuneManager.Tune tuneToPlay;

        if (fullLabel.contains("||")) {
            String[] parts  = fullLabel.split("\\|\\|", 2);
            displayLabel    = parts[0];
            String tunePath = parts[1];

            // Find matching tune in our list, or fall back to default
            tuneToPlay = tunes.stream()
                    .filter(t -> t.getId().equals(tunePath))
                    .findFirst()
                    .orElse(tunes.get(0));
        } else {
            displayLabel = fullLabel;
            tuneToPlay   = tunes.get(0); // default beep
        }

        SoundEngine.play(tuneToPlay);
        statusLabel.setText("Alarm fired: " + displayLabel);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Alarm!");
        alert.setHeaderText(displayLabel.isBlank()
                ? "Alarm at " + alarm.getTime() : displayLabel);
        alert.setContentText("What would you like to do?");

        ButtonType snoozeBtn  = new ButtonType("Snooze 5 min");
        ButtonType dismissBtn = new ButtonType("Dismiss",
                ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(snoozeBtn, dismissBtn);

        Optional<ButtonType> result = alert.showAndWait();
        SoundEngine.stopCurrent();
        if (result.isPresent() && result.get() == snoozeBtn) {
            alarmManager.snooze(alarm);
            statusLabel.setText("Snoozed for 5 minutes");
        } else {
            statusLabel.setText("Alarm dismissed");
        }
    }

    // ─── Cancel resets sound too ──────────────────────────────────────

    @FXML
    private void handleCancel() {
        selectedHour   = 1;
        selectedMinute = 0;
        isPm           = false;
        editingHour    = true;
        amBtn.setSelected(true);
        pmBtn.setSelected(false);
        labelField.clear();
        repeatCheck.setSelected(false);
        soundPickerPanel.setVisible(false);
        soundPickerPanel.setManaged(false);

        // Reset tune to default
        selectTune(tunes.get(0));
        tuneListView.getSelectionModel().selectFirst();

        refreshDisplay();
        statusLabel.setText("");
    }

    // ─── All existing methods below — unchanged ───────────────────────

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

    private void drawDial() {
        GraphicsContext gc = dialCanvas.getGraphicsContext2D();
        double w  = dialCanvas.getWidth();
        double h  = dialCanvas.getHeight();
        double cx = w / 2, cy = h / 2;
        double r  = Math.min(w, h) / 2 - 4;

        gc.clearRect(0, 0, w, h);
        gc.setFill(Color.web("#ede7f6"));
        gc.fillOval(cx - r, cy - r, r * 2, r * 2);

        int count = editingHour ? 12 : 60;
        int step  = editingHour ? 1  : 5;
        gc.setFont(Font.font("System", FontWeight.NORMAL, r * 0.13));
        gc.setTextAlign(TextAlignment.CENTER);

        for (int i = step; i <= count; i += step) {
            double angle = Math.toRadians(i * (360.0 / count) - 90);
            double nr    = r * 0.78;
            double nx    = cx + nr * Math.cos(angle);
            double ny    = cy + nr * Math.sin(angle) + r * 0.05;
            String lbl   = editingHour
                    ? String.valueOf(i)
                    : String.format("%02d", i == 60 ? 0 : i);
            gc.setFill(Color.web("#7c6fa0"));
            gc.fillText(lbl, nx, ny);
        }

        double handAngle = editingHour
                ? Math.toRadians(selectedHour * 30 - 90)
                : Math.toRadians(selectedMinute * 6 - 90);

        double handR = r * 0.62;
        gc.setStroke(Color.web("#7c3aed"));
        gc.setLineWidth(2);
        gc.strokeLine(cx, cy,
                cx + handR * Math.cos(handAngle),
                cy + handR * Math.sin(handAngle));

        double dotR  = r * 0.13;
        double dotRv = r * 0.78;
        double dx    = cx + dotRv * Math.cos(handAngle);
        double dy    = cy + dotRv * Math.sin(handAngle);
        gc.setFill(Color.web("#7c3aed"));
        gc.fillOval(dx - dotR, dy - dotR, dotR * 2, dotR * 2);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("System", FontWeight.BOLD, r * 0.13));
        String selLbl = editingHour
                ? String.valueOf(selectedHour)
                : String.format("%02d", selectedMinute);
        gc.fillText(selLbl, dx, dy + r * 0.05);

        gc.setFill(Color.web("#7c3aed"));
        gc.fillOval(cx - 5, cy - 5, 10, 10);
    }

    @FXML private void handleDialPress(MouseEvent e) { updateFromDial(e); }
    @FXML private void handleDialDrag(MouseEvent e)  { updateFromDial(e); }

    private void updateFromDial(MouseEvent e) {
        double cx    = dialCanvas.getWidth()  / 2;
        double cy    = dialCanvas.getHeight() / 2;
        double angle = Math.toDegrees(Math.atan2(e.getY()-cy, e.getX()-cx)) + 90;
        if (angle < 0) angle += 360;

        if (editingHour) {
            int h = (int) Math.round(angle / 30);
            if (h == 0) h = 12;
            if (h > 12) h = 12;
            selectedHour = h;
        } else {
            selectedMinute = (int) Math.round(angle / 6) % 60;
        }

        if (editingHour && e.getEventType() == MouseEvent.MOUSE_PRESSED)
            editingHour = false;

        refreshDisplay();
    }

    @FXML
    private void handleSnoozeToggle() {
        snoozeToggle.getStyleClass().removeAll("ios-toggle-on");
        if (snoozeToggle.isSelected())
            snoozeToggle.getStyleClass().add("ios-toggle-on");
    }

    @FXML
    private void handleDeleteAlarm() {
        Alarm sel = alarmListView.getSelectionModel().getSelectedItem();
        if (sel != null) alarms.remove(sel);
    }

    public void shutdown() {
        if (alarmManager != null) alarmManager.stop();
    }
}