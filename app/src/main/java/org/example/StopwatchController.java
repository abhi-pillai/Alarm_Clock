package org.example;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class StopwatchController {

    @FXML private Label    mainTimeLabel;
    @FXML private Label    millisLabel;
    @FXML private Label    statusLabel;
    @FXML private Button   startStopBtn;
    @FXML private Button   lapBtn;
    @FXML private Button   resetBtn;
    @FXML private HBox     lapHeader;
    @FXML private ListView<Lap> lapListView;

    // ── State ──
    private long    elapsedMs       = 0;   // total elapsed milliseconds
    private long    lapStartMs      = 0;   // elapsed at start of current lap
    private long    tickStartNano   = 0;   // System.nanoTime() at last start
    private boolean running         = false;
    private int     lapCount        = 0;

    private final ObservableList<Lap> laps =
            FXCollections.observableArrayList();

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?>        tickFuture;

    // ─── Init ────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "stopwatch-thread");
            t.setDaemon(true);
            return t;
        });

        lapListView.setItems(laps);
        lapListView.setCellFactory(lv -> new LapCell());

        updateDisplay(0);
        setButtonStates(false, false);
    }

    // ─── Start / Stop ────────────────────────────────────────────────

    @FXML
    private void handleStartStop() {
        if (running) {
            stopwatch();
        } else {
            startWatch();
        }
    }

    private void startWatch() {
        running       = true;
        tickStartNano = System.nanoTime();

        startStopBtn.setText("⏸");
        statusLabel.setText(lapCount == 0 ? "running" : "running · lap " + (lapCount + 1));
        setButtonStates(true, true);

        // Tick every 10ms for smooth centisecond display
        tickFuture = scheduler.scheduleAtFixedRate(() -> {
            long nowNano    = System.nanoTime();
            long deltaNano  = nowNano - tickStartNano;
            long totalMs    = elapsedMs + deltaNano / 1_000_000;
            Platform.runLater(() -> updateDisplay(totalMs));
        }, 0, 10, TimeUnit.MILLISECONDS);
    }

    private void stopwatch() {
        // Capture elapsed before cancelling
        long nowNano   = System.nanoTime();
        elapsedMs     += (nowNano - tickStartNano) / 1_000_000;

        running = false;
        if (tickFuture != null) tickFuture.cancel(false);

        startStopBtn.setText("▶");
        statusLabel.setText("paused");
        setButtonStates(true, false);
        updateDisplay(elapsedMs);
    }

    // ─── Lap ─────────────────────────────────────────────────────────

    @FXML
    private void handleLap() {
        if (!running) return;

        long nowNano  = System.nanoTime();
        long totalMs  = elapsedMs + (nowNano - tickStartNano) / 1_000_000;
        long splitMs  = totalMs - lapStartMs;

        lapCount++;
        Lap lap = new Lap(lapCount, splitMs, totalMs);

        // Add to top of list (newest first)
        laps.add(0, lap);

        lapStartMs = totalMs;

        // Show header on first lap
        if (!lapHeader.isVisible()) {
            lapHeader.setVisible(true);
            lapHeader.setManaged(true);
        }

        statusLabel.setText("running · lap " + (lapCount + 1));
        refreshLapColors();
    }

    // ─── Reset ───────────────────────────────────────────────────────

    @FXML
    private void handleReset() {
        if (running) {
            stopwatch();
        }

        elapsedMs  = 0;
        lapStartMs = 0;
        lapCount   = 0;
        laps.clear();

        lapHeader.setVisible(false);
        lapHeader.setManaged(false);

        startStopBtn.setText("▶");
        statusLabel.setText("ready");
        setButtonStates(false, false);
        updateDisplay(0);
    }

    // ─── Display helpers ─────────────────────────────────────────────

    private void updateDisplay(long totalMs) {
        long minutes = totalMs / 60000;
        long seconds = (totalMs % 60000) / 1000;
        long millis  = (totalMs % 1000) / 10;

        mainTimeLabel.setText(String.format("%02d:%02d", minutes, seconds));
        millisLabel.setText(String.format(".%02d", millis));
    }

    // Highlight best (green) and worst (red) lap splits
    private void refreshLapColors() {
        if (laps.size() < 2) return;
        lapListView.refresh();
    }

    private void setButtonStates(boolean hasStarted, boolean isRunning) {
        // Lap button — enabled only while running
        lapBtn.setDisable(!isRunning);
        lapBtn.getStyleClass().removeAll("sw-btn-disabled");
        if (!isRunning) lapBtn.getStyleClass().add("sw-btn-disabled");

        // Reset button — enabled once we have elapsed time
        resetBtn.setDisable(!hasStarted);
        resetBtn.getStyleClass().removeAll("sw-btn-disabled");
        if (!hasStarted) resetBtn.getStyleClass().add("sw-btn-disabled");
    }

    // ─── Lap cell ────────────────────────────────────────────────────

    private class LapCell extends ListCell<Lap> {

        private final HBox  row       = new HBox();
        private final Label numLbl    = new Label();
        private final Label splitLbl  = new Label();
        private final Label deltaLbl  = new Label();

        LapCell() {
            row.setAlignment(Pos.CENTER_LEFT);
            row.setSpacing(0);

            numLbl.setPrefWidth(80);
            splitLbl.setPrefWidth(100);
            HBox.setHgrow(splitLbl,
                    javafx.scene.layout.Priority.ALWAYS);
            deltaLbl.setPrefWidth(90);
            deltaLbl.setAlignment(Pos.CENTER_RIGHT);

            numLbl.getStyleClass().add("sw-lap-num");
            splitLbl.getStyleClass().add("sw-lap-split");
            deltaLbl.getStyleClass().add("sw-lap-delta");

            row.getChildren().addAll(numLbl, splitLbl, deltaLbl);
            row.getStyleClass().add("sw-lap-row");
        }

        @Override
        protected void updateItem(Lap lap, boolean empty) {
            super.updateItem(lap, empty);

            if (empty || lap == null) {
                setGraphic(null);
                setStyle("-fx-background-color: transparent;");
                return;
            }

            numLbl.setText("Lap " + lap.getNumber());
            splitLbl.setText(Lap.format(lap.getSplitMs()));

            // Compute delta from average split
            long avgSplit = laps.isEmpty() ? 0
                    : laps.stream()
                           .mapToLong(Lap::getSplitMs)
                           .sum() / laps.size();

            long delta   = lap.getSplitMs() - avgSplit;
            String sign  = delta >= 0 ? "+" : "−";
            deltaLbl.setText(sign + Lap.format(Math.abs(delta)));

            // Find best and worst splits
            long best  = laps.stream()
                    .mapToLong(Lap::getSplitMs).min().orElse(0);
            long worst = laps.stream()
                    .mapToLong(Lap::getSplitMs).max().orElse(0);

            // Reset styles
            row.getStyleClass().removeAll(
                    "sw-lap-row-best", "sw-lap-row-worst");
            numLbl.getStyleClass().removeAll(
                    "sw-col-best", "sw-col-worst");
            splitLbl.getStyleClass().removeAll(
                    "sw-col-best", "sw-col-worst");
            deltaLbl.getStyleClass().removeAll(
                    "sw-col-best", "sw-col-worst");

            // Only colour best/worst when we have 2+ laps
            if (laps.size() >= 2) {
                if (lap.getSplitMs() == best) {
                    row.getStyleClass().add("sw-lap-row-best");
                    numLbl.getStyleClass().add("sw-col-best");
                    splitLbl.getStyleClass().add("sw-col-best");
                    deltaLbl.getStyleClass().add("sw-col-best");
                    numLbl.setText("Lap " + lap.getNumber() + "  ★");
                } else if (lap.getSplitMs() == worst) {
                    row.getStyleClass().add("sw-lap-row-worst");
                    numLbl.getStyleClass().add("sw-col-worst");
                    splitLbl.getStyleClass().add("sw-col-worst");
                    deltaLbl.getStyleClass().add("sw-col-worst");
                    numLbl.setText("Lap " + lap.getNumber() + "  ✕");
                }
            }

            setGraphic(row);
            setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────

    public void shutdown() {
        if (tickFuture != null) tickFuture.cancel(true);
        if (scheduler  != null) scheduler.shutdownNow();
    }
}