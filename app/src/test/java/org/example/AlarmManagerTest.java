package org.example;

import javafx.application.Platform;
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

    // ── JavaFX toolkit bootstrap ──────────────────────────────────────
    // Platform.runLater() requires the FX toolkit to be initialized.
    // We start it once for the entire test class and never shut it down
    // (shutting down is irreversible within the same JVM process).

    @BeforeAll
    static void initJavaFX() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            // This works if no FX app has started yet
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            // Toolkit already running (e.g. another test class started it)
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS),
                "JavaFX toolkit did not start in time");
    }

    // Helper — waits for all pending Platform.runLater() calls to finish
    private static void flushFX() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        assertTrue(latch.await(3, TimeUnit.SECONDS),
                "FX queue did not flush in time");
    }

    // ── snooze ────────────────────────────────────────────────────────

    @Test
    void snooze_addsOneAlarmToList() throws InterruptedException {
        ObservableList<Alarm> alarms = FXCollections.observableArrayList();
        AlarmManager manager = new AlarmManager(alarms, a -> {});

        manager.snooze(new Alarm(
                LocalTime.of(7, 30), "Test", false, "__default__"));

        flushFX(); // wait for Platform.runLater inside snooze()

        assertEquals(1, alarms.size(),
                "Snooze should add exactly one alarm");
    }

    @Test
    void snooze_newAlarm_isApproximatelyFiveMinutesFromNow()
            throws InterruptedException {
        ObservableList<Alarm> alarms = FXCollections.observableArrayList();
        AlarmManager manager = new AlarmManager(alarms, a -> {});

        manager.snooze(new Alarm(
                LocalTime.now(), "Test", false, "__default__"));
        flushFX();

        LocalTime expected = LocalTime.now().plusMinutes(5);
        long diff = Math.abs(
                alarms.get(0).getTime().toSecondOfDay() -
                expected.toSecondOfDay());

        assertTrue(diff <= 2,
                "Snooze time should be within 2s of 5 minutes from now");
    }

    @Test
    void snooze_isNeverRepeat() throws InterruptedException {
        ObservableList<Alarm> alarms = FXCollections.observableArrayList();
        AlarmManager manager = new AlarmManager(alarms, a -> {});

        // Even if the original alarm is daily, snooze must be one-time
        manager.snooze(new Alarm(
                LocalTime.now(), "Daily alarm", true, "__default__"));
        flushFX();

        assertFalse(alarms.get(0).isRepeat(),
                "Snoozed alarm must never be repeating");
    }

    @Test
    void snooze_labelContainsSnoozed() throws InterruptedException {
        ObservableList<Alarm> alarms = FXCollections.observableArrayList();
        AlarmManager manager = new AlarmManager(alarms, a -> {});

        manager.snooze(new Alarm(
                LocalTime.now(), "Wake up", false, "__default__"));
        flushFX();

        assertTrue(alarms.get(0).getLabel().contains("Snoozed"),
                "Snooze label should contain 'Snoozed'");
    }

    @Test
    void snooze_labelContainsOriginalLabel() throws InterruptedException {
        ObservableList<Alarm> alarms = FXCollections.observableArrayList();
        AlarmManager manager = new AlarmManager(alarms, a -> {});

        manager.snooze(new Alarm(
                LocalTime.now(), "Morning jog", false, "__default__"));
        flushFX();

        assertTrue(alarms.get(0).getLabel().contains("Morning jog"),
                "Snooze label should include original alarm label");
    }

    @Test
    void snooze_inheritsTuneId() throws InterruptedException {
        ObservableList<Alarm> alarms = FXCollections.observableArrayList();
        AlarmManager manager = new AlarmManager(alarms, a -> {});

        manager.snooze(new Alarm(
                LocalTime.now(), "Music alarm", false, "/path/birds.wav"));
        flushFX();

        assertEquals("/path/birds.wav", alarms.get(0).getTuneId(),
                "Snoozed alarm should inherit the original tune");
    }

    @Test
    void snooze_blankLabel_stillSnoozed() throws InterruptedException {
        ObservableList<Alarm> alarms = FXCollections.observableArrayList();
        AlarmManager manager = new AlarmManager(alarms, a -> {});

        manager.snooze(new Alarm(
                LocalTime.now(), "", false, "__default__"));
        flushFX();

        assertEquals(1, alarms.size());
        assertTrue(alarms.get(0).getLabel().startsWith("Snoozed"));
    }

    // ── start / stop ─────────────────────────────────────────────────

    @Test
    void start_thenStop_doesNotThrow() {
        ObservableList<Alarm> alarms = FXCollections.observableArrayList();
        AlarmManager manager = new AlarmManager(alarms, a -> {});
        assertDoesNotThrow(() -> {
            manager.start();
            manager.stop();
        });
    }

    @Test
    void stop_withoutStart_doesNotThrow() {
        ObservableList<Alarm> alarms = FXCollections.observableArrayList();
        AlarmManager manager = new AlarmManager(alarms, a -> {});
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

    // ── checkAlarms — past time does not trigger ──────────────────────

    @Test
    void pastAlarm_doesNotTrigger() throws InterruptedException {
        ObservableList<Alarm> alarms = FXCollections.observableArrayList();
        List<Alarm> triggered = new ArrayList<>();

        alarms.add(new Alarm(
                LocalTime.now().minusMinutes(1), "Past",
                false, "__default__"));

        AlarmManager manager = new AlarmManager(alarms, triggered::add);
        manager.start();

        // Wait 1.5s — enough for one full check cycle
        Thread.sleep(1500);
        flushFX();
        manager.stop();

        assertTrue(triggered.isEmpty(),
                "An alarm set 1 minute in the past should not trigger");
    }

    @Test
    void futureAlarm_doesNotTrigger() throws InterruptedException {
        ObservableList<Alarm> alarms = FXCollections.observableArrayList();
        List<Alarm> triggered = new ArrayList<>();

        alarms.add(new Alarm(
                LocalTime.now().plusMinutes(1), "Future",
                false, "__default__"));

        AlarmManager manager = new AlarmManager(alarms, triggered::add);
        manager.start();

        Thread.sleep(1500);
        flushFX();
        manager.stop();

        assertTrue(triggered.isEmpty(),
                "A future alarm should not trigger yet");
    }

    // ── empty alarm list ──────────────────────────────────────────────

    @Test
    void emptyAlarmList_checksWithoutCrashing() throws InterruptedException {
        ObservableList<Alarm> alarms = FXCollections.observableArrayList();
        AlarmManager manager = new AlarmManager(alarms, a ->
                fail("Should not trigger with empty list"));

        manager.start();
        Thread.sleep(1500);
        manager.stop();
        // No assertion needed — test passes if no exception thrown
    }
}