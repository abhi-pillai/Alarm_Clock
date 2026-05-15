package org.example;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Alarm {

    private final LocalTime time;
    private final String    label;
    private final boolean   repeat; // true = daily, false = one-time

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");

    public Alarm(LocalTime time, String label, boolean repeat) {
        this.time   = time;
        this.label  = label;
        this.repeat = repeat;
    }

    public LocalTime getTime()   { return time; }
    public String    getLabel()  { return label; }
    public boolean   isRepeat()  { return repeat; }

    @Override
    public String toString() {
        String base = time.format(DISPLAY_FORMAT);
        String tag  = repeat ? " ↻" : " ✕"; // ↻ = daily, ✕ = one-time
        return (label.isBlank() ? base : base + "  —  " + label) + tag;
    }
}