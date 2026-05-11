package org.example;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Alarm {

    private final LocalTime time;   // The alarm time e.g. 07:30
    private final String label;     // Optional label e.g. "Wake up"

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");

    public Alarm(LocalTime time, String label) {
        this.time  = time;
        this.label = label;
    }

    public LocalTime getTime()  { return time; }
    public String    getLabel() { return label; }

    // What gets shown in the alarm list — "07:30 — Wake up"
    @Override
    public String toString() {
        String base = time.format(DISPLAY_FORMAT);
        return label.isBlank() ? base : base + "  —  " + label;
    }
}