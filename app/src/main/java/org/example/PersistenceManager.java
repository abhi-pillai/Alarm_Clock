package org.example;

import java.io.*;
import java.nio.file.*;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class PersistenceManager {

    private static final Path SAVE_FILE = Paths.get(
            System.getProperty("user.home"), ".alarmclock", "alarms.txt");

    // Format: "HH:mm|label|repeat|tuneId"
    public static void save(List<Alarm> alarms) {
        try {
            Files.createDirectories(SAVE_FILE.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(SAVE_FILE)) {
                for (Alarm a : alarms) {
                    w.write(a.getTime()    + "|"
                          + a.getLabel()   + "|"
                          + a.isRepeat()   + "|"
                          + a.getTuneId());
                    w.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to save alarms: " + e.getMessage());
        }
    }

    public static List<Alarm> load() {
        List<Alarm> alarms = new ArrayList<>();
        if (!Files.exists(SAVE_FILE)) return alarms;

        try (BufferedReader r = Files.newBufferedReader(SAVE_FILE)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isBlank()) continue;

                // Support old 3-field format gracefully
                String[] p = line.split("\\|", 4);
                if (p.length < 3) continue;

                LocalTime time   = LocalTime.parse(p[0]);
                String    label  = p[1];
                boolean   repeat = Boolean.parseBoolean(p[2]);
                String    tuneId = p.length >= 4
                        ? p[3] : TuneManager.Tune.DEFAULT_ID;

                alarms.add(new Alarm(time, label, repeat, tuneId));
            }
        } catch (IOException e) {
            System.err.println("Failed to load alarms: " + e.getMessage());
        }

        return alarms;
    }
}