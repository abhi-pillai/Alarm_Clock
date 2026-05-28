package org.example;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClockController {

    @FXML private Canvas       clockCanvas;
    @FXML private Label        digitalLabel;
    @FXML private Label        dateLabel;
    @FXML private Label        tzLabel;
    @FXML private ToggleButton formatToggle;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEEE, dd MMM");
    private static final DateTimeFormatter FMT_12 =
            DateTimeFormatter.ofPattern("hh:mm:ss a");
    private static final DateTimeFormatter FMT_24 =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private boolean use24hr = false;
    private ScheduledExecutorService scheduler;

    @FXML
    public void initialize() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "clock-thread");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() ->
            Platform.runLater(this::tick),
        0, 1, TimeUnit.SECONDS);
    }

    // Called every second on the UI thread
    private void tick() {
        ZonedDateTime now = ZonedDateTime.now();

        // Show time in clock-label, date and tz separately
        String time = now.format(use24hr ? FMT_24 : FMT_12);
        String date = now.format(DATE_FMT);
        String tz   = now.getZone().getDisplayName(
                java.time.format.TextStyle.SHORT,
                java.util.Locale.getDefault())
                + "  ·  UTC" + now.getOffset();

        digitalLabel.setText(time);
        dateLabel.setText(date);
        tzLabel.setText(tz);

        drawClock(now);
    }

    @FXML
    private void handleFormatToggle() {
        use24hr = formatToggle.isSelected();
        formatToggle.setText(use24hr ? "Switch to 12hr" : "Switch to 24hr");
    }

    // ─── Analog Drawing ───────────────────────────────────────────────

    private void drawClock(ZonedDateTime now) {
        GraphicsContext gc = clockCanvas.getGraphicsContext2D();

        double w  = clockCanvas.getWidth();
        double h  = clockCanvas.getHeight();
        double cx = w / 2;          // center x
        double cy = h / 2;          // center y
        double r  = Math.min(w, h) / 2 - 8; // radius with small margin

        // 1. Clear previous frame
        gc.clearRect(0, 0, w, h);

        // 2. Outer bezel (dark ring)
        gc.setFill(Color.web("#e8f4f1"));
        gc.fillOval(cx - r - 8, cy - r - 8, (r + 8) * 2, (r + 8) * 2);

        // 3. Clock face (white circle)
        gc.setFill(Color.web("#ffffff"));
        gc.fillOval(cx - r, cy - r, r * 2, r * 2);

        // 4. Tick marks (60 small, 12 large)
        drawTicks(gc, cx, cy, r);

        // 5. Hour numbers (1–12)
        drawNumbers(gc, cx, cy, r);

        // 6. Extract time components
        int hour   = now.getHour() % 12;   // 0–11
        int minute = now.getMinute();       // 0–59
        int second = now.getSecond();       // 0–59

        // Smooth angles (hours and minutes move gradually, not in jumps)
        // Hour hand: full rotation = 12 hours = 43200 seconds
        double hourAngle   = Math.toRadians(
                (hour * 3600 + minute * 60 + second) / 43200.0 * 360 - 90);
        // Minute hand: full rotation = 60 minutes = 3600 seconds
        double minuteAngle = Math.toRadians(
                (minute * 60 + second) / 3600.0 * 360 - 90);
        // Second hand: discrete tick each second
        double secondAngle = Math.toRadians(second / 60.0 * 360 - 90);

        // 7. Draw hands (longest = minute, medium = hour, thin red = second)
        drawHand(gc, cx, cy, hourAngle,   r * 0.55, 7, Color.web("#1a1a18"));
        drawHand(gc, cx, cy, minuteAngle, r * 0.80, 5, Color.web("#1a1a18"));
        drawHand(gc, cx, cy, secondAngle, r * 0.85, 2, Color.web("#4a7c6f"));

        // 8. Center dot (covers hand pivot)
        gc.setFill(Color.web("#4a7c6f"));
        gc.fillOval(cx - 7, cy - 7, 14, 14);
        gc.setFill(Color.web("#ffffff"));
        gc.fillOval(cx - 3, cy - 3, 6, 6);
    }

    private void drawTicks(GraphicsContext gc, double cx, double cy, double r) {
        for (int i = 0; i < 60; i++) {
            double angle     = Math.toRadians(i * 6 - 90); // 6° per tick
            boolean isMajor  = (i % 5 == 0);               // major at every 5

            double innerR    = isMajor ? r * 0.82 : r * 0.90;
            double outerR    = r * 0.97;

            double x1 = cx + innerR * Math.cos(angle);
            double y1 = cy + innerR * Math.sin(angle);
            double x2 = cx + outerR * Math.cos(angle);
            double y2 = cy + outerR * Math.sin(angle);

            gc.setStroke(Color.web(isMajor ? "#4a7c6f" : "#c2d8d3"));
            gc.setLineWidth(isMajor ? 2.5 : 1.0);
            gc.strokeLine(x1, y1, x2, y2);
        }
    }

    private void drawNumbers(GraphicsContext gc, double cx, double cy, double r) {
        gc.setFill(Color.web("#5a5a55")); 
        gc.setTextAlign(TextAlignment.CENTER);

        // Scale font with clock size
        double fontSize = r * 0.18;
        gc.setFont(Font.font("System", javafx.scene.text.FontWeight.BOLD, fontSize));

        for (int i = 1; i <= 12; i++) {
            // Numbers sit at 72% of radius so they clear the tick marks
            double angle  = Math.toRadians(i * 30 - 90);
            double numR   = r * 0.72;
            double x      = cx + numR * Math.cos(angle);
            double y      = cy + numR * Math.sin(angle) + fontSize * 0.35;
            gc.fillText(String.valueOf(i), x, y);
        }
    }

    // Draws a single clock hand as a rounded line
    private void drawHand(GraphicsContext gc,
                          double cx, double cy,
                          double angle,
                          double length,
                          double width,
                          Color color) {
        double x = cx + length * Math.cos(angle);
        double y = cy + length * Math.sin(angle);

        gc.setStroke(color);
        gc.setLineWidth(width);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.strokeLine(cx, cy, x, y);
    }

    public void shutdown() {
        if (scheduler != null) scheduler.shutdownNow();
    }
}