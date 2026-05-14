package org.example;

import java.io.*;
import java.nio.file.*;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class PersistenceManager {

    // Saves to the user's home directory — works on Windows, Mac, Linux
    private static final Path SAVE_FILE = Paths.get(
            System.getProperty("user.home"), ".alarmclock", "alarms.txt");

    // Each alarm is saved as one line: "HH:mm|Label"
    // Example: "07:30|Wake up"
    public static void save(List<Alarm> alarms) {
        try {
            // Create the directory if it doesn't exist yet
            Files.createDirectories(SAVE_FILE.getParent());

            try (BufferedWriter writer = Files.newBufferedWriter(SAVE_FILE)) {
                for (Alarm alarm : alarms) {
                    writer.write(alarm.getTime() + "|" + alarm.getLabel());
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to save alarms: " + e.getMessage());
        }
    }

    // Reads the file and reconstructs Alarm objects
    public static List<Alarm> load() {
        List<Alarm> alarms = new ArrayList<>();

        if (!Files.exists(SAVE_FILE)) return alarms; // First run — no file yet

        try (BufferedReader reader = Files.newBufferedReader(SAVE_FILE)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isBlank()) continue;

                String[] parts = line.split("\\|", 2); // Split on | into max 2 parts
                if (parts.length < 2) continue;        // Skip malformed lines

                LocalTime time  = LocalTime.parse(parts[0]);
                String label    = parts[1];
                alarms.add(new Alarm(time, label));
            }
        } catch (IOException e) {
            System.err.println("Failed to load alarms: " + e.getMessage());
        }

        return alarms;
    }
}