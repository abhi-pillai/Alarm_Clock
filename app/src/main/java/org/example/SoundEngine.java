package org.example;

import javafx.application.Platform;
import javafx.scene.control.Alert;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class SoundEngine {

    // Tracks the active clip (for file playback) so we can stop it instantly
    private static final AtomicReference<Clip> activeClip =
            new AtomicReference<>(null);

    // Tracks the active line (for beep playback) so we can stop it instantly
    private static final AtomicReference<SourceDataLine> activeLine =
            new AtomicReference<>(null);

    // ─── Public API ──────────────────────────────────────────────────

    public static void play(TuneManager.Tune tune) {
        stopCurrent();
        if (tune == null || tune.isDefault()) {
            playBeepAsync();
        } else {
            playFileAsync(tune.getId());
        }
    }

    // Stop whatever is currently playing — called on dismiss/snooze/reset
    public static void stopCurrent() {
        Clip clip = activeClip.getAndSet(null);
        if (clip != null) {
            clip.stop();
            clip.close();
        }

        SourceDataLine line = activeLine.getAndSet(null);
        if (line != null) {
            line.stop();
            line.flush();
            line.close();
        }
    }

    // ─── File playback ───────────────────────────────────────────────

    private static void playFileAsync(String absolutePath) {
        Thread t = new Thread(() -> {
            File file = new File(absolutePath);

            if (!file.exists()) {
                System.err.println("Audio file not found: " + absolutePath);
                playBeepSync();
                return;
            }

            String name = file.getName().toLowerCase();

            try {
                if (name.endsWith(".wav")) {
                    playWav(file);
                } else if (name.endsWith(".mp3")) {
                    playMp3(file);
                } else {
                    System.err.println("Unsupported format: " + name);
                    playBeepSync();
                }
            } catch (Exception e) {
                System.err.println("Playback error: " + e.getMessage());
                e.printStackTrace();
                playBeepSync();
            }

        }, "sound-file-thread");

        t.setDaemon(true);
        t.start();
    }

    // ─── WAV playback ────────────────────────────────────────────────

    private static void playWav(File file) throws Exception {
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(file)) {

            AudioFormat baseFormat = raw.getFormat();

            // Convert to PCM_SIGNED if needed (some WAVs are PCM_UNSIGNED etc.)
            AudioFormat pcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false
            );

            AudioInputStream pcmStream = AudioSystem.isConversionSupported(
                    pcmFormat, baseFormat)
                    ? AudioSystem.getAudioInputStream(pcmFormat, raw)
                    : raw; // already PCM — use as-is

            playStream(pcmStream,
                    pcmStream.getFormat().equals(baseFormat)
                            ? baseFormat : pcmFormat);
        }
    }

    // ─── MP3 playback (requires mp3spi + tritonus-share on classpath) ──

    private static void playMp3(File file) throws Exception {
        // Step 1: open raw MP3 stream
        // mp3spi registers itself as an AudioSystem SPI provider,
        // so AudioSystem.getAudioInputStream() handles MP3 automatically
        AudioInputStream mp3Stream =
                AudioSystem.getAudioInputStream(file);

        AudioFormat mp3Format = mp3Stream.getFormat();

        // Step 2: decode MP3 → PCM_SIGNED (what the sound card understands)
        AudioFormat pcmFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                mp3Format.getSampleRate(),  // e.g. 44100.0
                16,                          // 16-bit samples
                mp3Format.getChannels(),     // stereo = 2
                mp3Format.getChannels() * 2, // frame size
                mp3Format.getSampleRate(),
                false                        // little-endian
        );

        AudioInputStream pcmStream =
                AudioSystem.getAudioInputStream(pcmFormat, mp3Stream);

        playStream(pcmStream, pcmFormat);
    }

    // ─── Shared stream → Clip player ────────────────────────────────

    private static void playStream(AudioInputStream stream,
                                    AudioFormat format) throws Exception {
        DataLine.Info info = new DataLine.Info(Clip.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            System.err.println("Audio line not supported for format: " + format);
            playBeepSync();
            return;
        }

        Clip clip = (Clip) AudioSystem.getLine(info);
        clip.open(stream);

        // Register before starting so stopCurrent() can reach it
        activeClip.set(clip);
        clip.start();

        System.out.println("Playing: " + format);

        // Block until done or stopped externally
        while (clip.isRunning() && activeClip.get() != null) {
            Thread.sleep(100);
        }

        // Clean up only if not already closed by stopCurrent()
        if (activeClip.compareAndSet(clip, null)) {
            clip.close();
        }
    }

    // ─── Generated beep ──────────────────────────────────────────────

    private static void playBeepAsync() {
        Thread t = new Thread(SoundEngine::playBeepSync, "alarm-beep-thread");
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
            activeLine.set(line);
            line.start();

            for (int beep = 0; beep < 3; beep++) {
                if (activeLine.get() == null) return;
                writeTone(line, 880, 400);
                writeSilence(line, 200);
            }

            line.drain();
            if (activeLine.compareAndSet(line, null)) {
                line.close();
            }

        } catch (LineUnavailableException e) {
            System.err.println("Audio line unavailable: " + e.getMessage());
        }
    }

    // ─── Tone generation ─────────────────────────────────────────────

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
        int    samples = 44100 * durationMs / 1000;
        byte[] silence = new byte[samples * 2];
        line.write(silence, 0, silence.length);
    }
}