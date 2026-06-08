package org.example;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TuneManagerTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void redirect() throws Exception {
        Path tempFile = tempDir.resolve("tunes.txt");
        Field field = TuneManager.class.getDeclaredField("TUNES_FILE");
        field.setAccessible(true);
        field.set(null, tempFile);
    }

    // ── Default tune ──────────────────────────────────────────────────

    @Test
    void loadAll_alwaysIncludesDefaultAsFirst() {
        List<TuneManager.Tune> tunes = TuneManager.loadAll();
        assertFalse(tunes.isEmpty());
        assertTrue(tunes.get(0).isDefault());
        assertEquals("Default beep", tunes.get(0).getName());
    }

    @Test
    void defaultTune_hasCorrectId() {
        TuneManager.Tune def = TuneManager.loadAll().get(0);
        assertEquals(TuneManager.Tune.DEFAULT_ID, def.getId());
    }

    // ── Save / load ───────────────────────────────────────────────────

    @Test
    void save_thenLoad_returnsUserTunes() throws Exception {
        // Create a real temp file so "exists" check passes
        Path fakeAudio = tempDir.resolve("birds.wav");
        Files.writeString(fakeAudio, "fake");

        TuneManager.Tune userTune = new TuneManager.Tune(
                fakeAudio.toString(), "Birds");

        List<TuneManager.Tune> toSave = List.of(
                new TuneManager.Tune(TuneManager.Tune.DEFAULT_ID, "Default beep"),
                userTune
        );

        TuneManager.save(toSave);
        List<TuneManager.Tune> loaded = TuneManager.loadAll();

        assertEquals(2, loaded.size());
        assertEquals("Birds",               loaded.get(1).getName());
        assertEquals(fakeAudio.toString(),  loaded.get(1).getId());
    }

    @Test
    void save_skipsDefaultTune() throws Exception {
        List<TuneManager.Tune> tunes = List.of(
                new TuneManager.Tune(TuneManager.Tune.DEFAULT_ID, "Default beep")
        );
        TuneManager.save(tunes);

        // File should be empty — default is never persisted
        Path file = tempDir.resolve("tunes.txt");
        assertTrue(Files.exists(file));
        assertEquals(0, Files.readAllLines(file).size());
    }

    @Test
    void load_missingFile_returnsEmptyUserTunes() {
        List<TuneManager.Tune> tunes = TuneManager.loadAll();
        assertEquals(1, tunes.size(), "Only default should be present");
    }

    @Test
    void load_removesEntriesWhereFileNoLongerExists() throws Exception {
        Path file = tempDir.resolve("tunes.txt");
        // Point to a file that doesn't exist
        Files.writeString(file,
                "/nonexistent/path/deleted.wav|Ghost tune\n");

        List<TuneManager.Tune> loaded = TuneManager.loadAll();
        assertEquals(1, loaded.size(),
                "Missing audio file should be filtered out");
    }

    // ── Tune model ────────────────────────────────────────────────────

    @Test
    void tune_toString_returnsName() {
        TuneManager.Tune t = new TuneManager.Tune("/path/file.wav", "Forest");
        assertEquals("Forest", t.toString());
    }

    @Test
    void tune_isDefault_onlyForDefaultId() {
        assertTrue(new TuneManager.Tune(
                TuneManager.Tune.DEFAULT_ID, "x").isDefault());
        assertFalse(new TuneManager.Tune(
                "/some/path.wav", "y").isDefault());
    }
}