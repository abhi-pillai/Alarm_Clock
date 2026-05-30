package org.example;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Alarm {

    private final LocalTime time;
    private final String    label;
    private final boolean   repeat;
    private final String    tuneId; // absolute path or "__default__"

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");

    public Alarm(LocalTime time, String label,
                 boolean repeat, String tuneId) {
        this.time   = time;
        this.label  = label;
        this.repeat = repeat;
        this.tuneId = tuneId != null ? tuneId : TuneManager.Tune.DEFAULT_ID;
    }

    public LocalTime getTime()   { return time;   }
    public String    getLabel()  { return label;  }
    public boolean   isRepeat()  { return repeat; }
    public String    getTuneId() { return tuneId; }

    @Override
    public String toString() {
        String base = time.format(DISPLAY_FORMAT);
        String tag  = repeat ? " ↻" : " ✕";
        return (label.isBlank() ? base : base + "  —  " + label) + tag;
    }
}