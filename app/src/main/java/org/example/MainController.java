package org.example;

import javafx.fxml.FXML;

public class MainController {

    @FXML private ClockController      clockController;
    @FXML private AlarmController      alarmController;
    @FXML private TimerController      timerController;
    @FXML private StopwatchController  stopwatchController;

    public void shutdown() {
        if (clockController     != null) clockController.shutdown();
        if (alarmController     != null) alarmController.shutdown();
        if (timerController     != null) timerController.shutdown();
        if (stopwatchController != null) stopwatchController.shutdown();
    }
}