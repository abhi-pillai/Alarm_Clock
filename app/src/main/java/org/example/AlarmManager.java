package org.example;

import javafx.application.Platform;
import javafx.collections.ObservableList;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AlarmManager {

    private final ObservableList<Alarm> alarms;

    // Callback — controller gives us a method to call when alarm fires
    // We use Runnable so AlarmManager doesn't need to know about the UI
    private final java.util.function.Consumer<Alarm> onAlarmTriggered;

    // Tracks which alarms already fired this minute so they don't
    // trigger multiple times within the same minute
    private final Set<Alarm> firedThisMinute = new HashSet<>();

    private ScheduledExecutorService scheduler;

    private static final DateTimeFormatter MINUTE_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");

    public AlarmManager(ObservableList<Alarm> alarms,
                        java.util.function.Consumer<Alarm> onAlarmTriggered) {
        this.alarms             = alarms;
        this.onAlarmTriggered   = onAlarmTriggered;
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "alarm-checker-thread");
            t.setDaemon(true);
            return t;
        });

        // Check every second if any alarm matches current time
        scheduler.scheduleAtFixedRate(this::checkAlarms, 0, 1, TimeUnit.SECONDS);
    }

    private void checkAlarms() {
        // Get current time rounded to HH:mm (ignore seconds)
        String currentMinute = LocalTime.now().format(MINUTE_FORMAT);

        for (Alarm alarm : alarms) {
            String alarmMinute = alarm.getTime().format(MINUTE_FORMAT);

            boolean timeMatches    = currentMinute.equals(alarmMinute);
            boolean alreadyFired   = firedThisMinute.contains(alarm);

            if (timeMatches && !alreadyFired) {
                firedThisMinute.add(alarm);

                // Hand off to UI thread to show the alert
                Platform.runLater(() -> onAlarmTriggered.accept(alarm));
            }

            // Once the minute has passed, reset so it can fire again tomorrow
            if (!timeMatches) {
                firedThisMinute.remove(alarm);
            }
        }
    }

    // Snooze: adds a NEW alarm 5 minutes from now, one-time
    public void snooze(Alarm original) {
        LocalTime snoozeTime = LocalTime.now().plusMinutes(5);
        Alarm snoozeAlarm = new Alarm(snoozeTime, "Snoozed: " + original.getLabel());

        // Must update the ObservableList on the UI thread
        Platform.runLater(() -> alarms.add(snoozeAlarm));
    }

    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }
}