package org.example;

import javafx.fxml.FXML;
// import javafx.fxml.FXMLLoader;

public class MainController {

    // fx:include injects child controllers via this naming convention:
    // source="clock.fxml" → field name must be "clockController"
    @FXML private ClockController clockController;
    @FXML private AlarmController alarmController;
    @FXML private TimerController timerController;

    public void shutdown() {
        if (clockController != null) clockController.shutdown();
        if (alarmController != null) alarmController.shutdown();
        if (timerController != null) timerController.shutdown();
    }
}