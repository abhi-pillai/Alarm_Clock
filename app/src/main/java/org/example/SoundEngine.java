package org.example;

import javax.sound.sampled.*;

public class SoundEngine {

    // Plays a beeping alarm sound using pure Java — no audio file needed
    public static void playAlarm() {
        // Run on a separate thread so it doesn't freeze the UI
        Thread t = new Thread(() -> {
            try {
                // Audio format: 44100 Hz, 16-bit, mono, signed, big-endian
                AudioFormat format = new AudioFormat(44100, 16, 1, true, true);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(format);
                line.start();

                // Beep 3 times
                for (int beep = 0; beep < 3; beep++) {
                    writeTone(line, 880, 400);  // 880 Hz tone, 400ms
                    writeSilence(line, 200);     // 200ms silence between beeps
                }

                line.drain();
                line.close();

            } catch (LineUnavailableException e) {
                System.err.println("Audio unavailable: " + e.getMessage());
            }
        }, "alarm-sound-thread");

        t.setDaemon(true);
        t.start();
    }

    // Generates a sine wave tone at the given frequency for durationMs milliseconds
    private static void writeTone(SourceDataLine line, int frequencyHz, int durationMs) {
        int sampleRate  = 44100;
        int numSamples  = sampleRate * durationMs / 1000;
        byte[] buffer   = new byte[numSamples * 2]; // 16-bit = 2 bytes per sample

        for (int i = 0; i < numSamples; i++) {
            // Sine wave formula: amplitude * sin(2π * frequency * time)
            double angle    = 2.0 * Math.PI * frequencyHz * i / sampleRate;
            short sample    = (short) (Short.MAX_VALUE * 0.7 * Math.sin(angle));

            // Write 16-bit sample as two bytes (big-endian)
            buffer[2 * i]     = (byte) (sample >> 8);
            buffer[2 * i + 1] = (byte) (sample & 0xFF);
        }

        line.write(buffer, 0, buffer.length);
    }

    // Writes silence (zeroed bytes) for durationMs milliseconds
    private static void writeSilence(SourceDataLine line, int durationMs) {
        int numSamples = 44100 * durationMs / 1000;
        byte[] silence = new byte[numSamples * 2];
        line.write(silence, 0, silence.length);
    }
}