package org.example;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class SoundEngine {

    // Play by tune — dispatches to file or default
    public static void play(TuneManager.Tune tune) {
        if (tune == null || tune.isDefault()) {
            playAlarm();
        } else {
            playFile(tune.getId());
        }
    }

    // Plays a .wav or .mp3 file from an absolute path
    public static void playFile(String absolutePath) {
        Thread t = new Thread(() -> {
            try {
                File file = new File(absolutePath);
                if (!file.exists()) {
                    // Fall back to default if file missing
                    playAlarmSync();
                    return;
                }

                // AudioSystem supports .wav natively
                // For .mp3 you'd need an extra library — we'll handle that below
                try (AudioInputStream stream =
                             AudioSystem.getAudioInputStream(file)) {

                    AudioFormat baseFormat   = stream.getFormat();
                    AudioFormat decodedFormat = new AudioFormat(
                            AudioFormat.Encoding.PCM_SIGNED,
                            baseFormat.getSampleRate(),
                            16,
                            baseFormat.getChannels(),
                            baseFormat.getChannels() * 2,
                            baseFormat.getSampleRate(),
                            false);

                    try (AudioInputStream decoded =
                                 AudioSystem.getAudioInputStream(
                                         decodedFormat, stream)) {

                        DataLine.Info info =
                                new DataLine.Info(Clip.class, decodedFormat);
                        Clip clip = (Clip) AudioSystem.getLine(info);
                        clip.open(decoded);
                        clip.start();

                        // Wait for clip to finish
                        Thread.sleep(clip.getMicrosecondLength() / 1000);
                        clip.close();
                    }
                }

            } catch (UnsupportedAudioFileException e) {
                System.err.println("Unsupported audio format: " + e.getMessage());
                playAlarmSync();
            } catch (Exception e) {
                System.err.println("Audio playback failed: " + e.getMessage());
                playAlarmSync();
            }
        }, "sound-file-thread");

        t.setDaemon(true);
        t.start();
    }

    // Default generated beep (existing logic, now also used as fallback)
    public static void playAlarm() {
        Thread t = new Thread(() -> playAlarmSync(), "alarm-sound-thread");
        t.setDaemon(true);
        t.start();
    }

    // Synchronous version so it can be called from within another thread
    private static void playAlarmSync() {
        try {
            AudioFormat format = new AudioFormat(44100, 16, 1, true, true);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();

            for (int beep = 0; beep < 3; beep++) {
                writeTone(line, 880, 400);
                writeSilence(line, 200);
            }

            line.drain();
            line.close();
        } catch (LineUnavailableException e) {
            System.err.println("Audio unavailable: " + e.getMessage());
        }
    }

    private static void writeTone(SourceDataLine line,
                                   int frequencyHz, int durationMs) {
        int sampleRate = 44100;
        int numSamples = sampleRate * durationMs / 1000;
        byte[] buffer  = new byte[numSamples * 2];

        for (int i = 0; i < numSamples; i++) {
            double angle = 2.0 * Math.PI * frequencyHz * i / sampleRate;
            short sample = (short) (Short.MAX_VALUE * 0.7 * Math.sin(angle));
            buffer[2 * i]     = (byte) (sample >> 8);
            buffer[2 * i + 1] = (byte) (sample & 0xFF);
        }
        line.write(buffer, 0, buffer.length);
    }

    private static void writeSilence(SourceDataLine line, int durationMs) {
        int numSamples = 44100 * durationMs / 1000;
        byte[] silence = new byte[numSamples * 2];
        line.write(silence, 0, silence.length);
    }
}