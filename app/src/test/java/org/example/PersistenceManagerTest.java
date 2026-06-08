package org.example;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceManagerTest {

    @TempDir
    Path tempDir;

    // Redirect the SAVE_FILE path to our temp dir before each test
    @BeforeEach
    void redirectSaveFile() throws Exception {
        Path tempFile = tempDir.resolve("alarms.txt");
        Field field = PersistenceManager.class.getDeclaredField("SAVE_FILE");
        field.setAccessible(true);
        field.set(null, tempFile);
    }

    // ── Save ──────────────────────────────────────────────────────────

    @Test
    void save_createsFile() {
        PersistenceManager.save(List.of(
            new Alarm(LocalTime.of(7, 30), "Test", false, "__default__")
        ));
        Path file = tempDir.resolve("alarms.txt");
        assertTrue(Files.exists(file), "Save file should be created");
    }

    @Test
    void save_emptyList_createsEmptyFile() throws IOException {
        PersistenceManager.save(List.of());
        Path file = tempDir.resolve("alarms.txt");
        assertTrue(Files.exists(file));
        assertEquals(0, Files.readAllLines(file).size());
    }

    // ── Load ──────────────────────────────────────────────────────────

    @Test
    void load_noFile_returnsEmptyList() {
        List<Alarm> alarms = PersistenceManager.load();
        assertNotNull(alarms);
        assertTrue(alarms.isEmpty());
    }

    @Test
    void load_afterSave_returnsCorrectAlarms() {
        List<Alarm> original = List.of(
            new Alarm(LocalTime.of(7,  30), "Wake up",  true,  "__default__"),
            new Alarm(LocalTime.of(9,  0),  "Stand-up", false, "__default__"),
            new Alarm(LocalTime.of(22, 0),  "",         false, "/some/tune.wav")
        );

        PersistenceManager.save(original);
        List<Alarm> loaded = PersistenceManager.load();

        assertEquals(3, loaded.size());

        assertEquals(LocalTime.of(7, 30), loaded.get(0).getTime());
        assertEquals("Wake up",           loaded.get(0).getLabel());
        assertTrue(                        loaded.get(0).isRepeat());
        assertEquals("__default__",        loaded.get(0).getTuneId());

        assertEquals(LocalTime.of(9, 0),  loaded.get(1).getTime());
        assertFalse(                       loaded.get(1).isRepeat());

        assertEquals("/some/tune.wav",     loaded.get(2).getTuneId());
        assertEquals("",                   loaded.get(2).getLabel());
    }

    // ── Round-trip ────────────────────────────────────────────────────

    @Test
    void roundTrip_preservesAllFields() {
        Alarm original = new Alarm(
                LocalTime.of(14, 45), "Afternoon break",
                true, "/music/birds.mp3");

        PersistenceManager.save(List.of(original));
        Alarm loaded = PersistenceManager.load().get(0);

        assertEquals(original.getTime(),    loaded.getTime());
        assertEquals(original.getLabel(),   loaded.getLabel());
        assertEquals(original.isRepeat(),   loaded.isRepeat());
        assertEquals(original.getTuneId(),  loaded.getTuneId());
    }

    // ── Malformed lines ───────────────────────────────────────────────

    @Test
    void load_malformedLines_skipsThemGracefully() throws IOException {
        Path file = tempDir.resolve("alarms.txt");
        Files.writeString(file,
                "07:30|Wake up|true|__default__\n" +
                "this is not valid\n" +
                "\n" +
                "09:00|Stand-up|false|__default__\n");

        List<Alarm> loaded = PersistenceManager.load();
        assertEquals(2, loaded.size(),
                "Malformed lines should be skipped");
    }

    // ── Backward compat (3-field old format) ─────────────────────────

    @Test
    void load_oldThreeFieldFormat_usesDefaultTuneId() throws IOException {
        Path file = tempDir.resolve("alarms.txt");
        // Old format before tuneId was added
        Files.writeString(file, "07:30|Wake up|true\n");

        List<Alarm> loaded = PersistenceManager.load();
        assertEquals(1, loaded.size());
        assertEquals(TuneManager.Tune.DEFAULT_ID, loaded.get(0).getTuneId());
    }
}