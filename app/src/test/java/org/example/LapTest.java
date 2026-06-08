package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class LapTest {

    // ── Construction ─────────────────────────────────────────────────

    @Test
    void constructor_storesAllFields() {
        Lap lap = new Lap(3, 4500L, 15000L);
        assertEquals(3,     lap.getNumber());
        assertEquals(4500L, lap.getSplitMs());
        assertEquals(15000L, lap.getTotalMs());
    }

    // ── Format ───────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "0,      00:00.00",
        "1000,   00:01.00",
        "61000,  01:01.00",
        "3661000,61:01.00",
        "999,    00:00.99",
        "1500,   00:01.50"
    })
    void format_producesCorrectString(long ms, String expected) {
        assertEquals(expected, Lap.format(ms));
    }

    @Test
    void format_zero_isAllZeros() {
        assertEquals("00:00.00", Lap.format(0));
    }

    @Test
    void format_largeValue_minutesCanExceed59() {
        // 2 hours = 7200000ms → 120:00.00
        assertEquals("120:00.00", Lap.format(7_200_000L));
    }

    // ── Split vs total ────────────────────────────────────────────────

    @Test
    void splitMs_isIndependentOfTotalMs() {
        Lap lap1 = new Lap(1, 5000L, 5000L);
        Lap lap2 = new Lap(2, 3000L, 8000L);

        assertEquals(5000L, lap1.getSplitMs());
        assertEquals(3000L, lap2.getSplitMs());
        assertEquals(8000L, lap2.getTotalMs());
    }

    @Test
    void lapNumber_incrementsCorrectly() {
        for (int i = 1; i <= 5; i++) {
            Lap lap = new Lap(i, 1000L * i, 1000L * i);
            assertEquals(i, lap.getNumber());
        }
    }
}