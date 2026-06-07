package org.example;

public class Lap {

    private final int    number;      // Lap 1, 2, 3...
    private final long   splitMs;     // this lap's duration in ms
    private final long   totalMs;     // total elapsed time at this lap

    public Lap(int number, long splitMs, long totalMs) {
        this.number  = number;
        this.splitMs = splitMs;
        this.totalMs = totalMs;
    }

    public int  getNumber()  { return number;  }
    public long getSplitMs() { return splitMs; }
    public long getTotalMs() { return totalMs; }

    // Format ms as MM:SS.mm
    public static String format(long ms) {
        long minutes = ms / 60000;
        long seconds = (ms % 60000) / 1000;
        long millis  = (ms % 1000) / 10; // two digits
        return String.format("%02d:%02d.%02d", minutes, seconds, millis);
    }
}