package org.example;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TuneManagerTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void redirect() {
        TuneManager.setTunesFile(tempDir.resolve("tunes.txt"));
    }

    @AfterEach
    void restore() {
        TuneManager.setTunesFile(
            java.nio.file.Paths.get(
                System.getProperty("user.home"),
                ".alarmclock", "tunes.txt"));
    }

    @Test
    void loadAll_alwaysIncludesDefaultAsFirst() {
        List<TuneManager.Tune> tunes = TuneManager.loadAll();
        assertFalse(tunes.isEmpty());
        assertTrue(tunes.get(0).isDefault());
        assertEquals("Default beep", tunes.get(0).getName());
    }

    @Test
    void defaultTune_hasCorrectId() {
        assertEquals(TuneManager.Tune.DEFAULT_ID,
                TuneManager.loadAll().get(0).getId());
    }

    @Test
    void save_thenLoad_returnsUserTunes() throws Exception {
        Path fakeAudio = tempDir.resolve("birds.wav");
        Files.writeString(fakeAudio, "fake");

        TuneManager.Tune userTune = new TuneManager.Tune(
                fakeAudio.toString(), "Birds");

        TuneManager.save(List.of(
            new TuneManager.Tune(TuneManager.Tune.DEFAULT_ID, "Default beep"),
            userTune
        ));

        List<TuneManager.Tune> loaded = TuneManager.loadAll();
        assertEquals(2, loaded.size());
        assertEquals("Birds",              loaded.get(1).getName());
        assertEquals(fakeAudio.toString(), loaded.get(1).getId());
    }

    @Test
    void save_skipsDefaultTune() throws Exception {
        TuneManager.save(List.of(
            new TuneManager.Tune(TuneManager.Tune.DEFAULT_ID, "Default beep")
        ));
        assertEquals(0,
            Files.readAllLines(tempDir.resolve("tunes.txt")).size());
    }

    @Test
    void load_missingFile_returnsOnlyDefault() {
        List<TuneManager.Tune> tunes = TuneManager.loadAll();
        assertEquals(1, tunes.size());
    }

    @Test
    void load_removesEntriesWhereFileNoLongerExists() throws Exception {
        Path file = tempDir.resolve("tunes.txt");
        Files.writeString(file,
            "/nonexistent/path/deleted.wav|Ghost tune\n");

        List<TuneManager.Tune> loaded = TuneManager.loadAll();
        assertEquals(1, loaded.size(), "Missing file should be filtered");
    }

    @Test
    void tune_toString_returnsName() {
        assertEquals("Forest",
            new TuneManager.Tune("/path/file.wav", "Forest").toString());
    }

    @Test
    void tune_isDefault_onlyForDefaultId() {
        assertTrue(new TuneManager.Tune(
                TuneManager.Tune.DEFAULT_ID, "x").isDefault());
        assertFalse(new TuneManager.Tune(
                "/some/path.wav", "y").isDefault());
    }
}