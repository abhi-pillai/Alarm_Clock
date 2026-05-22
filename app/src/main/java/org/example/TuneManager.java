package org.example;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class TuneManager {

    // Stored alongside alarms in the same folder
    private static final Path TUNES_FILE = Paths.get(
            System.getProperty("user.home"), ".alarmclock", "tunes.txt");

    // Represents one entry in the sound list
    public static class Tune {
        public static final String DEFAULT_ID = "__default__";

        private final String id;     // unique — "__default__" or absolute file path
        private final String name;   // display name shown in the list

        public Tune(String id, String name) {
            this.id   = id;
            this.name = name;
        }

        public String getId()   { return id; }
        public String getName() { return name; }
        public boolean isDefault() { return DEFAULT_ID.equals(id); }

        @Override public String toString() { return name; }
    }

    // Returns the full list: default first, then user tunes from disk
    public static List<Tune> loadAll() {
        List<Tune> tunes = new ArrayList<>();
        tunes.add(new Tune(Tune.DEFAULT_ID, "Default beep")); // always first

        if (!Files.exists(TUNES_FILE)) return tunes;

        try (BufferedReader reader = Files.newBufferedReader(TUNES_FILE)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isBlank()) continue;

                // Each line: "absolute/path/to/file.wav|Display Name"
                String[] parts = line.split("\\|", 2);
                if (parts.length < 2) continue;

                String path = parts[0];
                String name = parts[1];

                // Only add if file still exists on disk
                if (Files.exists(Paths.get(path))) {
                    tunes.add(new Tune(path, name));
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load tunes: " + e.getMessage());
        }

        return tunes;
    }

    // Saves only the user-added tunes (not the default)
    public static void save(List<Tune> tunes) {
        try {
            Files.createDirectories(TUNES_FILE.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(TUNES_FILE)) {
                for (Tune t : tunes) {
                    if (t.isDefault()) continue; // skip the built-in default
                    writer.write(t.getId() + "|" + t.getName());
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to save tunes: " + e.getMessage());
        }
    }
}