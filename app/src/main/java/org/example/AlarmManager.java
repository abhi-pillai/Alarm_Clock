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
import java.util.function.Consumer;

public class AlarmManager {

    private final ObservableList<Alarm> alarms;
    private final Consumer<Alarm>       onAlarmTriggered;
    private final Set<Alarm>            firedThisMinute = new HashSet<>();
    private ScheduledExecutorService    scheduler;

    private static final DateTimeFormatter MINUTE_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");

    public AlarmManager(ObservableList<Alarm> alarms,
                        Consumer<Alarm> onAlarmTriggered) {
        this.alarms           = alarms;
        this.onAlarmTriggered = onAlarmTriggered;
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "alarm-checker-thread");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::checkAlarms, 0, 1, TimeUnit.SECONDS);
    }

    private void checkAlarms() {
        String currentMinute = LocalTime.now().format(MINUTE_FORMAT);

        // Iterate over a copy to avoid ConcurrentModificationException
        // when we remove one-time alarms inside the loop
        for (Alarm alarm : new java.util.ArrayList<>(alarms)) {
            String  alarmMinute  = alarm.getTime().format(MINUTE_FORMAT);
            boolean timeMatches  = currentMinute.equals(alarmMinute);
            boolean alreadyFired = firedThisMinute.contains(alarm);

            if (timeMatches && !alreadyFired) {
                firedThisMinute.add(alarm);
                Platform.runLater(() -> {
                    onAlarmTriggered.accept(alarm);

                    // One-time alarm: remove it after firing
                    if (!alarm.isRepeat()) {
                        alarms.remove(alarm);
                    }
                });
            }

            if (!timeMatches) {
                firedThisMinute.remove(alarm);
            }
        }
    }

    public void snooze(Alarm original) {
    LocalTime snoozeTime = LocalTime.now().plusMinutes(5);
    Alarm snoozeAlarm    = new Alarm(
            snoozeTime,
            "Snoozed: " + original.getLabel(),
            false,
            original.getTuneId()  // inherit the same tune as the original
    );
    Platform.runLater(() -> alarms.add(snoozeAlarm));
}

    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }
}