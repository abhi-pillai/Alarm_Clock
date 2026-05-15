package org.example;

import java.io.*;
import java.nio.file.*;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class PersistenceManager {

    private static final Path SAVE_FILE = Paths.get(
            System.getProperty("user.home"), ".alarmclock", "alarms.txt");

    // Format: "07:30|Wake up|true"
    public static void save(List<Alarm> alarms) {
        try {
            Files.createDirectories(SAVE_FILE.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(SAVE_FILE)) {
                for (Alarm alarm : alarms) {
                    writer.write(alarm.getTime() + "|"
                               + alarm.getLabel() + "|"
                               + alarm.isRepeat());
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to save alarms: " + e.getMessage());
        }
    }

    public static List<Alarm> load() {
        List<Alarm> alarms = new ArrayList<>();
        if (!Files.exists(SAVE_FILE)) return alarms;

        try (BufferedReader reader = Files.newBufferedReader(SAVE_FILE)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isBlank()) continue;

                String[] parts = line.split("\\|", 3); // Now 3 parts
                if (parts.length < 3) continue;

                LocalTime time   = LocalTime.parse(parts[0]);
                String    label  = parts[1];
                boolean   repeat = Boolean.parseBoolean(parts[2]);
                alarms.add(new Alarm(time, label, repeat));
            }
        } catch (IOException e) {
            System.err.println("Failed to load alarms: " + e.getMessage());
        }

        return alarms;
    }
}