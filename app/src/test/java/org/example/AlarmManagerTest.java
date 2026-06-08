package org.example;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.*;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AlarmManagerTest {

    // ── Snooze ────────────────────────────────────────────────────────

    @Test
    void snooze_addsNewAlarmFiveMinutesFromNow() throws InterruptedException {
        ObservableList<Alarm> alarms = FXCollections.observableArrayList();
        CountDownLatch latch = new CountDownLatch(1);

        AlarmManager manager = new AlarmManager(alarms, a -> {});

        Alarm original = new Alarm(
                LocalTime.now(), "Test", false, "__default__");

        // Listen for the snooze alarm being added
        alarms.addListener(
            (javafx.collections.ListChangeListener<Alarm>) c -> {
                while (c.next()) {
                    if (c.wasAdded()) latch.countDown();
                }
            });

        manager.snooze(original);

        // Wait up to 2s for Platform.runLater to fire
        assertTrue(latch.await(2, TimeUnit.SECONDS),
                "Snooze alarm should be added to list");

        Alarm snoozed = alarms.get(0);
        assertFalse(snoozed.isRepeat(), "Snooze is always one-time");
        assertTrue(snoozed.getLabel().contains("Snoozed"),
                "Snooze label should contain 'Snoozed'");
        assertEquals(original.getTuneId(), snoozed.getTuneId(),
                "Snooze should inherit original tune");

        // Check time is ~5 minutes from now
        LocalTime expected = LocalTime.now().plusMinutes(5);
        long diffSeconds = Math.abs(
                snoozed.getTime().toSecondOfDay() -
                expected.toSecondOfDay());
        assertTrue(diffSeconds <= 2,
                "Snooze time should be ~5 minutes from now");

        manager.stop();
    }

    @Test
    void snooze_inherits_tuneId_fromOriginal() throws InterruptedException {
        ObservableList<Alarm> alarms = FXCollections.observableArrayList();
        CountDownLatch latch = new CountDownLatch(1);

        AlarmManager manager = new AlarmManager(alarms, a -> {});

        Alarm original = new Alarm(
                LocalTime.now(), "Music alarm", false,
                "/path/to/birds.wav");

        alarms.addListener(
            (javafx.collections.ListChangeListener<Alarm>) c -> {
                while (c.next()) {
                    if (c.wasAdded()) latch.countDown();
                }
            });

        manager.snooze(original);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("/path/to/birds.wav",
                alarms.get(0).getTuneId());

        manager.stop();
    }

    // ── Stop ─────────────────────────────────────────────────────────

    @Test
    void stop_doesNotThrow() {
        ObservableList<Alarm> alarms = FXCollections.observableArrayList();
        AlarmManager manager = new AlarmManager(alarms, a -> {});
        manager.start();
        assertDoesNotThrow(manager::stop);
    }

    @Test
    void stop_calledTwice_doesNotThrow() {
        ObservableList<Alarm> alarms = FXCollections.observableArrayList();
        AlarmManager manager = new AlarmManager(alarms, a -> {});
        manager.start();
        manager.stop();
        assertDoesNotThrow(manager::stop);
    }

    // ── Alarm matching ────────────────────────────────────────────────

    @Test
    void alarm_withPastTime_doesNotTriggerImmediately()
            throws InterruptedException {
        ObservableList<Alarm> alarms = FXCollections.observableArrayList();
        List<Alarm> triggered = new ArrayList<>();

        // Alarm set to 1 minute ago — should NOT fire
        LocalTime pastTime = LocalTime.now().minusMinutes(1);
        alarms.add(new Alarm(pastTime, "Past", false, "__default__"));

        AlarmManager manager = new AlarmManager(alarms, triggered::add);
        manager.start();

        // Wait 1.5s — enough for one check cycle
        Thread.sleep(1500);
        manager.stop();

        assertTrue(triggered.isEmpty(),
                "Past alarm should not trigger");
    }
}