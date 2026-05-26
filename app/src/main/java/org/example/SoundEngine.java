package org.example;

import javax.sound.sampled.*;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

public class SoundEngine {

    // SourceDataLine works for both file and beep playback
    private static final AtomicReference<SourceDataLine> activeLine =
            new AtomicReference<>(null);

    private static volatile boolean stopRequested = false;

    // ─── Public API ──────────────────────────────────────────────────

    public static void play(TuneManager.Tune tune) {
        stopCurrent();
        if (tune == null || tune.isDefault()) {
            playBeepAsync();
        } else {
            playFileAsync(tune.getId());
        }
    }

    public static void stopCurrent() {
        stopRequested = true;

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
                System.err.println("File not found: " + absolutePath);
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

    private static void playWav(File file) throws Exception {
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(file)) {
            AudioFormat baseFormat = raw.getFormat();

            AudioFormat pcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false
            );

            AudioInputStream pcmStream =
                    AudioSystem.isConversionSupported(pcmFormat, baseFormat)
                    ? AudioSystem.getAudioInputStream(pcmFormat, raw)
                    : raw;

            streamAudio(pcmStream, pcmStream.getFormat());
        }
    }

    private static void playMp3(File file) throws Exception {
        // Step 1: open raw MP3 stream via mp3spi SPI
        AudioInputStream mp3Stream =
                AudioSystem.getAudioInputStream(file);

        AudioFormat mp3Format = mp3Stream.getFormat();
        System.out.println("MP3 source format: " + mp3Format);

        // Step 2: decode to PCM_SIGNED
        AudioFormat pcmFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                mp3Format.getSampleRate(),
                16,
                mp3Format.getChannels(),
                mp3Format.getChannels() * 2,
                mp3Format.getSampleRate(),
                false
        );

        AudioInputStream pcmStream =
                AudioSystem.getAudioInputStream(pcmFormat, mp3Stream);

        System.out.println("MP3 decoded format: " + pcmStream.getFormat());
        streamAudio(pcmStream, pcmFormat);
    }

    // ─── Core streaming method — uses SourceDataLine (not Clip) ──────
    // Streams audio in 4KB chunks so frame length -1 is never a problem

    private static void streamAudio(AudioInputStream stream,
                                     AudioFormat format) throws Exception {
        DataLine.Info info =
                new DataLine.Info(SourceDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            System.err.println("Line not supported: " + format);
            playBeepSync();
            return;
        }

        SourceDataLine line =
                (SourceDataLine) AudioSystem.getLine(info);
        line.open(format, 4096 * 4); // 4KB buffer
        activeLine.set(line);

        stopRequested = false;
        line.start();

        byte[] buffer = new byte[4096];
        int    bytesRead;

        // Stream in chunks until done or stop is requested
        while (!stopRequested &&
               (bytesRead = stream.read(buffer, 0, buffer.length)) != -1) {
            line.write(buffer, 0, bytesRead);
        }

        // Flush remaining audio in the buffer before closing
        if (!stopRequested) {
            line.drain();
        }

        // Clean up only if not already stopped externally
        if (activeLine.compareAndSet(line, null)) {
            line.stop();
            line.close();
        }

        stream.close();
        System.out.println("Playback complete.");
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

            stopRequested = false;
            line.start();

            for (int beep = 0; beep < 3; beep++) {
                if (stopRequested) break;
                writeTone(line, 880, 400);
                writeSilence(line, 200);
            }

            if (!stopRequested) line.drain();

            if (activeLine.compareAndSet(line, null)) {
                line.stop();
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