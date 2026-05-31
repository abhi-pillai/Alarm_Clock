package org.example;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public class SoundPickerController {

    @FXML private ListView<TuneManager.Tune> tuneListView;
    @FXML private Label  previewStatusLabel;
    @FXML private Button removeBtn;

    private ObservableList<TuneManager.Tune> tunes;
    private TuneManager.Tune                 selectedTune;

    // Called by AlarmController after loading the FXML
    public void init(ObservableList<TuneManager.Tune> tunes,
                     TuneManager.Tune                 currentSelection) {
        this.tunes        = tunes;
        this.selectedTune = currentSelection;

        tuneListView.setItems(tunes);

        // Wire selection listener
        tuneListView.getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, o, n) -> {
                if (n != null) {
                    selectedTune = n;
                    removeBtn.setDisable(n.isDefault());
                    previewStatusLabel.setText("");
                }
            });

        // Select current tune in list
        tuneListView.getSelectionModel().select(currentSelection);
        removeBtn.setDisable(
                currentSelection == null || currentSelection.isDefault());
    }

    // Returns whichever tune the user had selected when they closed
    public TuneManager.Tune getSelectedTune() {
        return selectedTune;
    }

    @FXML
    private void handlePreview() {
        TuneManager.Tune tune =
                tuneListView.getSelectionModel().getSelectedItem();
        if (tune == null) return;

        SoundEngine.stopCurrent();
        SoundEngine.play(tune);
        previewStatusLabel.setText("Playing: " + tune.getName() + "...");
    }

    @FXML
    private void handleStopPreview() {
        SoundEngine.stopCurrent();
        previewStatusLabel.setText("Stopped.");
    }

    @FXML
    private void handleAddTune() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select alarm tune");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter(
                    "Audio files", "*.wav", "*.mp3"),
            new FileChooser.ExtensionFilter("WAV", "*.wav"),
            new FileChooser.ExtensionFilter("MP3", "*.mp3")
        );

        File file = chooser.showOpenDialog(
                tuneListView.getScene().getWindow());

        if (file != null) {
            String raw  = file.getName();
            String name = raw.contains(".")
                    ? raw.substring(0, raw.lastIndexOf('.')) : raw;

            TuneManager.Tune newTune =
                    new TuneManager.Tune(file.getAbsolutePath(), name);

            boolean exists = tunes.stream()
                    .anyMatch(t -> t.getId().equals(newTune.getId()));

            if (!exists) {
                tunes.add(newTune);
                TuneManager.save(tunes);
            }

            tuneListView.getSelectionModel().select(newTune);
            previewStatusLabel.setText("Added: " + name);
        }
    }

    @FXML
    private void handleRemove() {
        TuneManager.Tune tune =
                tuneListView.getSelectionModel().getSelectedItem();
        if (tune == null || tune.isDefault()) return;

        tunes.remove(tune);
        TuneManager.save(tunes);
        tuneListView.getSelectionModel().selectFirst();
        previewStatusLabel.setText("Removed.");
    }

    @FXML
    private void handleDone() {
        // Stop any preview playing before closing
        SoundEngine.stopCurrent();

        // Close the popup window
        Stage stage = (Stage) tuneListView.getScene().getWindow();
        stage.close();
    }
}