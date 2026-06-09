package org.example;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceManagerTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void redirectSaveFile() {
        // No reflection needed — uses the package-private setter
        PersistenceManager.setSaveFile(tempDir.resolve("alarms.txt"));
    }

    @AfterEach
    void restoreDefault() {
        // Reset back so other tests / app runs aren't affected
        PersistenceManager.setSaveFile(
            java.nio.file.Paths.get(
                System.getProperty("user.home"),
                ".alarmclock", "alarms.txt"));
    }

    @Test
    void save_createsFile() {
        PersistenceManager.save(List.of(
            new Alarm(LocalTime.of(7, 30), "Test", false, "__default__")
        ));
        assertTrue(Files.exists(tempDir.resolve("alarms.txt")));
    }

    @Test
    void save_emptyList_createsEmptyFile() throws IOException {
        PersistenceManager.save(List.of());
        assertEquals(0,
            Files.readAllLines(tempDir.resolve("alarms.txt")).size());
    }

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
        assertEquals("/some/tune.wav",     loaded.get(2).getTuneId());
    }

    @Test
    void roundTrip_preservesAllFields() {
        Alarm original = new Alarm(
                LocalTime.of(14, 45), "Afternoon break",
                true, "/music/birds.mp3");

        PersistenceManager.save(List.of(original));
        Alarm loaded = PersistenceManager.load().get(0);

        assertEquals(original.getTime(),   loaded.getTime());
        assertEquals(original.getLabel(),  loaded.getLabel());
        assertEquals(original.isRepeat(),  loaded.isRepeat());
        assertEquals(original.getTuneId(), loaded.getTuneId());
    }

    @Test
    void load_malformedLines_skipsThemGracefully() throws IOException {
        Path file = tempDir.resolve("alarms.txt");
        Files.writeString(file,
            "07:30|Wake up|true|__default__\n" +
            "this is not valid\n"              +
            "\n"                               +
            "09:00|Stand-up|false|__default__\n");

        List<Alarm> loaded = PersistenceManager.load();
        assertEquals(2, loaded.size());
    }

    @Test
    void load_oldThreeFieldFormat_usesDefaultTuneId() throws IOException {
        Path file = tempDir.resolve("alarms.txt");
        Files.writeString(file, "07:30|Wake up|true\n");

        List<Alarm> loaded = PersistenceManager.load();
        assertEquals(1, loaded.size());
        assertEquals(TuneManager.Tune.DEFAULT_ID, loaded.get(0).getTuneId());
    }
}