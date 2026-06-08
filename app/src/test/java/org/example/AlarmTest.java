package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class AlarmTest {

    // ── Construction ─────────────────────────────────────────────────

    @Test
    void constructor_storesAllFields() {
        LocalTime time  = LocalTime.of(7, 30);
        Alarm     alarm = new Alarm(time, "Wake up", true, "__default__");

        assertEquals(time,        alarm.getTime());
        assertEquals("Wake up",   alarm.getLabel());
        assertTrue(               alarm.isRepeat());
        assertEquals("__default__", alarm.getTuneId());
    }

    @Test
    void constructor_nullTuneId_fallsBackToDefault() {
        Alarm alarm = new Alarm(LocalTime.of(8, 0), "Test", false, null);
        assertEquals(TuneManager.Tune.DEFAULT_ID, alarm.getTuneId());
    }

    // ── toString ─────────────────────────────────────────────────────

    @Test
    void toString_withLabel_includesTimeAndLabel() {
        Alarm alarm = new Alarm(LocalTime.of(7, 5), "Gym", false, null);
        String s = alarm.toString();
        assertTrue(s.contains("07:05"),  "Should contain formatted time");
        assertTrue(s.contains("Gym"),    "Should contain label");
        assertTrue(s.contains("✕"),      "One-time alarm should show ✕");
    }

    @Test
    void toString_repeatAlarm_showsRepeatSymbol() {
        Alarm alarm = new Alarm(LocalTime.of(9, 0), "Stand-up", true, null);
        assertTrue(alarm.toString().contains("↻"),
                "Repeat alarm should show ↻");
    }

    @Test
    void toString_blankLabel_showsTimeOnly() {
        Alarm alarm = new Alarm(LocalTime.of(6, 0), "", false, null);
        String s = alarm.toString();
        assertTrue(s.contains("06:00"));
        assertFalse(s.contains("—"), "Should not show separator with blank label");
    }

    // ── Time formatting ───────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "0,  0,  00:00",
        "7,  5,  07:05",
        "12, 0,  12:00",
        "23, 59, 23:59"
    })
    void time_formatsAsHHMM(int hour, int min, String expected) {
        Alarm alarm = new Alarm(LocalTime.of(hour, min), "", false, null);
        assertTrue(alarm.toString().startsWith(expected),
                "Expected toString to start with " + expected);
    }

    // ── Equality of time ─────────────────────────────────────────────

    @Test
    void twoAlarmsAtSameTime_haveMatchingGetTime() {
        LocalTime t = LocalTime.of(7, 30);
        Alarm a1 = new Alarm(t, "First",  false, null);
        Alarm a2 = new Alarm(t, "Second", true,  null);
        assertEquals(a1.getTime(), a2.getTime());
    }
}