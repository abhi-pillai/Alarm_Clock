package org.example;

import javax.sound.sampled.*;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

public class SoundEngine {

    // Holds the currently playing clip so we can stop it from any thread.
    // AtomicReference makes reads/writes thread-safe without synchronized blocks.
    private static final AtomicReference<Clip> activeClip =
            new AtomicReference<>(null);

    // Also track the SourceDataLine used for the generated beep
    private static final AtomicReference<SourceDataLine> activeLine =
            new AtomicReference<>(null);

    // ─── Public API ──────────────────────────────────────────────────

    public static void play(TuneManager.Tune tune) {
        stopCurrent(); // Always stop whatever is playing first
        if (tune == null || tune.isDefault()) {
            playBeepAsync();
        } else {
            playFileAsync(tune.getId());
        }
    }

    // Called when user clicks Dismiss or Snooze — stops immediately
    public static void stopCurrent() {
        // Stop file clip if active
        Clip clip = activeClip.getAndSet(null);
        if (clip != null && clip.isRunning()) {
            clip.stop();
            clip.close();
        }

        // Stop generated beep line if active
        SourceDataLine line = activeLine.getAndSet(null);
        if (line != null && line.isOpen()) {
            line.stop();
            line.flush();
            line.close();
        }
    }

    // ─── File playback (.wav and .mp3 via JLayer) ────────────────────

    private static void playFileAsync(String absolutePath) {
        Thread t = new Thread(() -> {
            try {
                File file = new File(absolutePath);
                if (!file.exists()) { playBeepSync(); return; }

                AudioInputStream stream =
                        AudioSystem.getAudioInputStream(file);

                AudioFormat baseFormat    = stream.getFormat();
                AudioFormat decodedFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        baseFormat.getSampleRate(),
                        16,
                        baseFormat.getChannels(),
                        baseFormat.getChannels() * 2,
                        baseFormat.getSampleRate(),
                        false);

                AudioInputStream decoded =
                        AudioSystem.getAudioInputStream(decodedFormat, stream);

                DataLine.Info info =
                        new DataLine.Info(Clip.class, decodedFormat);
                Clip clip = (Clip) AudioSystem.getLine(info);
                clip.open(decoded);

                // Register before starting so stopCurrent() can reach it
                activeClip.set(clip);
                clip.start();

                // Block this thread until the clip finishes or is stopped
                while (clip.isRunning()) {
                    Thread.sleep(100);
                }

                // Clean up only if we weren't already stopped externally
                if (activeClip.compareAndSet(clip, null)) {
                    clip.close();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (UnsupportedAudioFileException e) {
                System.err.println("Unsupported format: " + e.getMessage());
                playBeepSync();
            } catch (Exception e) {
                System.err.println("Playback failed: " + e.getMessage());
                playBeepSync();
            }
        }, "sound-file-thread");

        t.setDaemon(true);
        t.start();
    }

    // ─── Generated beep fallback ─────────────────────────────────────

    private static void playBeepAsync() {
        Thread t = new Thread(() -> playBeepSync(), "alarm-beep-thread");
        t.setDaemon(true);
        t.start();
    }

    private static void playBeepSync() {
        try {
            AudioFormat format = new AudioFormat(44100, 16, 1, true, true);
            DataLine.Info info =
                    new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine line =
                    (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);

            // Register so stopCurrent() can reach it
            activeLine.set(line);
            line.start();

            for (int beep = 0; beep < 3; beep++) {
                // Check before each beep if we've been stopped
                if (activeLine.get() == null) return;
                writeTone(line, 880, 400);
                writeSilence(line, 200);
            }

            line.drain();

            // Clean up only if not already stopped externally
            if (activeLine.compareAndSet(line, null)) {
                line.close();
            }

        } catch (LineUnavailableException e) {
            System.err.println("Audio unavailable: " + e.getMessage());
        }
    }

    // ─── Audio generation helpers ────────────────────────────────────

    private static void writeTone(SourceDataLine line,
                                   int frequencyHz, int durationMs) {
        int    sampleRate = 44100;
        int    numSamples = sampleRate * durationMs / 1000;
        byte[] buffer     = new byte[numSamples * 2];

        for (int i = 0; i < numSamples; i++) {
            double angle  = 2.0 * Math.PI * frequencyHz * i / sampleRate;
            short  sample = (short) (Short.MAX_VALUE * 0.7 * Math.sin(angle));
            buffer[2 * i]     = (byte) (sample >> 8);
            buffer[2 * i + 1] = (byte) (sample & 0xFF);
        }
        line.write(buffer, 0, buffer.length);
    }

    private static void writeSilence(SourceDataLine line, int durationMs) {
        int    numSamples = 44100 * durationMs / 1000;
        byte[] silence    = new byte[numSamples * 2];
        line.write(silence, 0, silence.length);
    }
}