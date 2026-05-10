package org.example;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class MainController {

    // @FXML links this variable to the fx:id="clockLabel" in your FXML file
    @FXML
    private Label clockLabel;

    // JavaFX calls this automatically after the FXML is loaded
    @FXML
    public void initialize() {
        // For now, just confirm it's wired up
        clockLabel.setText("12:00:00");
    }
}