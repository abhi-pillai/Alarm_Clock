package org.example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoundEngineTest {

    // ── stopCurrent on idle ───────────────────────────────────────────

    @Test
    void stopCurrent_whenNothingPlaying_doesNotThrow() {
        assertDoesNotThrow(SoundEngine::stopCurrent);
    }

    @Test
    void stopCurrent_calledMultipleTimes_doesNotThrow() {
        assertDoesNotThrow(() -> {
            SoundEngine.stopCurrent();
            SoundEngine.stopCurrent();
            SoundEngine.stopCurrent();
        });
    }

    // ── play with null / default ──────────────────────────────────────

    @Test
    void play_nullTune_doesNotThrow() {
        assertDoesNotThrow(() -> SoundEngine.play(null));
        // Give thread 100ms to start then stop it
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        SoundEngine.stopCurrent();
    }

    @Test
    void play_defaultTune_doesNotThrow() {
        TuneManager.Tune def = new TuneManager.Tune(
                TuneManager.Tune.DEFAULT_ID, "Default beep");
        assertDoesNotThrow(() -> SoundEngine.play(def));
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        SoundEngine.stopCurrent();
    }

    // ── play with missing file ────────────────────────────────────────

    @Test
    void play_nonExistentFile_doesNotThrow() {
        TuneManager.Tune ghost = new TuneManager.Tune(
                "/nonexistent/path/ghost.wav", "Ghost");
        assertDoesNotThrow(() -> SoundEngine.play(ghost));
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        SoundEngine.stopCurrent();
    }

    @Test
    void play_nonExistentMp3_doesNotThrow() {
        TuneManager.Tune ghost = new TuneManager.Tune(
                "/nonexistent/path/ghost.mp3", "Ghost MP3");
        assertDoesNotThrow(() -> SoundEngine.play(ghost));
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        SoundEngine.stopCurrent();
    }

    // ── stop while playing ────────────────────────────────────────────

    @Test
    void stopCurrent_whileBeepPlaying_doesNotThrow() throws InterruptedException {
        SoundEngine.play(null); // starts beep on background thread
        Thread.sleep(150);      // let it start
        assertDoesNotThrow(SoundEngine::stopCurrent);
    }
}