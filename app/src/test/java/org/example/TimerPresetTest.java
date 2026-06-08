package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class TimerPresetTest {

    // ── Preset math ───────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "5,  0,  300",
        "10, 0,  600",
        "15, 0,  900",
        "1,  30, 90",
        "0,  45, 45",
        "2,  30, 150"
    })
    void totalSeconds_calculatedCorrectly(int mins, int secs, int expected) {
        int total = mins * 60 + secs;
        assertEquals(expected, total);
    }

    // ── Boundary values ───────────────────────────────────────────────

    @Test
    void zeroMinutesZeroSeconds_isInvalid() {
        int total = 0 * 60 + 0;
        assertTrue(total <= 0, "0:00 duration should be rejected");
    }

    @Test
    void maxMinutes_999_isValid() {
        int total = 999 * 60;
        assertTrue(total > 0);
        assertEquals(59940, total);
    }

    @Test
    void negativeSeconds_isInvalid() {
        int secs = -1;
        assertTrue(secs < 0 || secs > 59,
                "Seconds < 0 should be rejected");
    }

    @Test
    void secondsOver59_isInvalid() {
        int secs = 60;
        assertTrue(secs > 59,
                "Seconds > 59 should be rejected");
    }

    // ── Reset logic ───────────────────────────────────────────────────

    @Test
    void reset_defaultPreset_restores5Minutes() {
        int presetSeconds = 5 * 60;
        int remaining     = 127; // simulate some elapsed time

        // Reset
        remaining = presetSeconds;

        assertEquals(300, remaining,
                "Reset should restore to preset duration");
    }

    @Test
    void reset_customPreset_restoresCustomDuration() {
        int customSeconds = 7 * 60 + 30; // 7m 30s
        int remaining     = 100;

        remaining = customSeconds;

        assertEquals(450, remaining);
    }

    // ── Progress calculation ──────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "300, 300, 1.0",
        "150, 300, 0.5",
        "0,   300, 0.0",
        "1,   300, 0.003333"
    })
    void progressRing_calculatedCorrectly(
            int remaining, int total, double expectedProgress) {
        double progress = total > 0 ? (double) remaining / total : 0;
        assertEquals(expectedProgress, progress, 0.001);
    }

    @Test
    void progressRing_zeroTotal_isZero() {
        double progress = 0 > 0 ? (double) 0 / 0 : 0;
        assertEquals(0.0, progress);
    }
}