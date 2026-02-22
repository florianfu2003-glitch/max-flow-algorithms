package ega.gui;

import ega.core.Edge;
import ega.core.Graph;
import ega.generator.GraphGenerator;
import ega.gui.vis.VisFrame;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import ega.algorithms.EdmondsKarp;
import ega.algorithms.FordFulkerson;
import ega.algorithms.Dinic;
import ega.algorithms.GoldbergTarjan;

import ega.testbed.TestEnvironment;

/**
 * Main application window for the max-flow visualization tool.
 *
 * <p>Layout:
 * <ul>
 *   <li><b>Top control bar</b>: algorithm selection, random graph generation, stepping, auto-play, reset,
 *       playback speed, test environment, log export/clear.</li>
 *   <li><b>Center</b>: {@link GraphCanvas} rendering the base graph and the current {@link VisFrame}.</li>
 *   <li><b>Bottom</b>: log output area for runtime feedback and test reports.</li>
 * </ul>
 *
 * <p>Execution model:
 * <ul>
 *   <li>Whenever a graph is generated or the selected algorithm changes, the chosen algorithm is
 *       <b>pre-run</b> on a cloned graph to generate a frame sequence.</li>
 *   <li>Pre-runs and test environments are executed via {@link SwingWorker} to avoid blocking the EDT.</li>
 *   <li>Auto-play uses a Swing {@link Timer} whose delay is controlled by a slider.</li>
 * </ul>
 */
public class MainWindow extends JFrame {

    /* ===================== UI controls ===================== */

    private JComboBox<String> algorithmSelect;
    private JButton btnGenerateGraph;
    private JButton btnStep;
    private JButton btnAutoPlay;
    private JButton btnReset;
    private JSlider speedSlider;
    private JButton btnRunTestEnv;
    private JButton btnSaveLog;
    private JButton btnClearLog;

    private JSpinner spinnerN;
    private JSpinner spinnerMaxCap;

    private GraphCanvas canvas;
    private JTextArea logArea;

    /* ===================== Current graph & playback state ===================== */

    private Graph currentGraph;
    private int currentS = -1;
    private int currentT = -1;
    private double[] currentX;
    private double[] currentY;

    private List<VisFrame> frames = new ArrayList<>();
    private int frameIdx = -1;
    private Timer player;

    public MainWindow() {
        setTitle("Max-Flow Visualization Tool");
        setLayout(new BorderLayout());

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 12));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        controlPanel.setPreferredSize(new Dimension(0, 120));

        algorithmSelect = new JComboBox<>(new String[]{
                "Ford-Fulkerson",
                "Edmonds-Karp",
                "Dinic",
                "Goldberg-Tarjan"
        });
        algorithmSelect.setToolTipText("Select the algorithm to pre-run and visualize");

        btnGenerateGraph = new JButton("Generate the graph(");
        btnGenerateGraph.setToolTipText("Generate a random planar-ish directed graph with capacities");

        btnStep = new JButton("Next step");
        btnStep.setToolTipText("Advance by one visualization frame");

        btnAutoPlay = new JButton("Auto-play");
        btnAutoPlay.setToolTipText("Auto-play / pause at the current speed");

        btnReset = new JButton("Reset steps");
        btnReset.setToolTipText("Reset to the beginning (keep the current graph and frame sequence)");

        speedSlider = new JSlider(50, 2000, 500);
        speedSlider.setPaintTicks(true);
        speedSlider.setPaintLabels(true);
        speedSlider.setMajorTickSpacing(500);
        speedSlider.setToolTipText("Frame interval for auto-play (milliseconds)");

        spinnerN = new JSpinner(new SpinnerNumberModel(12, 3, 200, 1));
        spinnerMaxCap = new JSpinner(new SpinnerNumberModel(50, 1, 999, 1));

        btnRunTestEnv = new JButton("Test environment");
        btnRunTestEnv.setToolTipText("Run a quick/full test batch and print the report to the log area");

        btnSaveLog = new JButton("Save log");
        btnSaveLog.setToolTipText("Export the log contents as a text file");

        btnClearLog = new JButton("Clear log");
        btnClearLog.setToolTipText("Clear the log area");

        controlPanel.add(new JLabel("Algorithms："));
        controlPanel.add(algorithmSelect);
        controlPanel.add(new JLabel("Nodes n："));
        controlPanel.add(spinnerN);
        controlPanel.add(new JLabel("Capacity upper bound："));
        controlPanel.add(spinnerMaxCap);
        controlPanel.add(btnGenerateGraph);
        controlPanel.add(btnStep);
        controlPanel.add(btnAutoPlay);
        controlPanel.add(btnReset);
        controlPanel.add(new JLabel("Speed ms："));
        controlPanel.add(speedSlider);
        controlPanel.add(btnRunTestEnv);
        controlPanel.add(btnSaveLog);
        controlPanel.add(btnClearLog);

        add(controlPanel, BorderLayout.NORTH);

        canvas = new GraphCanvas();
        add(canvas, BorderLayout.CENTER);

        logArea = new JTextArea(6, 60);
        logArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(
                logArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );
        add(scroll, BorderLayout.SOUTH);

        player = new Timer(speedSlider.getValue(), e -> stepOnce());
        speedSlider.addChangeListener(e -> player.setDelay(speedSlider.getValue()));

        btnGenerateGraph.addActionListener(e -> generateNewGraph());
        btnStep.addActionListener(e -> stepOnce());
        btnAutoPlay.addActionListener(e -> toggleAuto());
        btnReset.addActionListener(e -> resetToStart());
        btnRunTestEnv.addActionListener(e -> chooseAndRunTestEnv());
        btnSaveLog.addActionListener(e -> saveLogToFile());
        btnClearLog.addActionListener(e -> {
            logArea.setText("");
            appendLog("Log cleared。\n");
        });

        // When the algorithm selection changes, re-run the precomputation (in the background) for the current graph.
        algorithmSelect.addActionListener(e -> preRunSelectedAlgorithmAsync());

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 750);
        setLocationRelativeTo(null);
    }

    /**
     * Generates a new random graph, displays it, and triggers a background pre-run of the selected algorithm.
     */
    private void generateNewGraph() {
        int n = (Integer) spinnerN.getValue();
        int maxCap = (Integer) spinnerMaxCap.getValue();

        GraphGenerator.Result res = GraphGenerator.generate(n, maxCap, new Random());
        this.currentGraph = res.graph.cloneGraph(); // keep a dedicated copy for the GUI state
        this.currentS = res.s;
        this.currentT = res.t;
        this.currentX = res.x;
        this.currentY = res.y;

        canvas.setGraphData(currentGraph, currentX, currentY, currentS, currentT);

        int m = countOriginalEdges(currentGraph);
        appendLog(String.format("Generate a random graph：n=%d, m=%d, s=%d, t=%d\n", n, m, currentS, currentT));

        preRunSelectedAlgorithmAsync();
    }

    /**
     * Counts the number of original directed edges in the residual network.
     * Original edges are identified by {@code origCap > 0} (reverse residual edges have {@code origCap == 0}).
     */
    private int countOriginalEdges(Graph g) {
        int cnt = 0;
        for (int u = 0; u < g.size(); u++) {
            for (Edge e : g.adj(u)) {
                if (e.origCap > 0) cnt++;
            }
        }
        return cnt;
    }

    /**
     * Pre-runs the currently selected algorithm to generate the visualization frame sequence.
     *
     * <p>The algorithm is executed on a clone of {@code currentGraph} so that the GUI's graph data remains stable.</p>
     */
    private void preRunSelectedAlgorithmAsync() {
        if (currentGraph == null) {
            appendLog("Please generate the graph first, then switch algorithms to run a pre-execution.\n");
            return;
        }

        final String algo = (String) algorithmSelect.getSelectedItem();

        setControlsEnabled(false);
        player.stop();
        canvas.clearFrame();
        frames = new ArrayList<>();
        frameIdx = -1;

        new SwingWorker<PreRunResult, String>() {
            @Override
            protected PreRunResult doInBackground() {
                List<VisFrame> seq = new ArrayList<>();
                Graph work = currentGraph.cloneGraph();
                long f = -1;

                long t0 = System.currentTimeMillis();
                try {
                    switch (algo) {
                        case "Edmonds-Karp":
                            f = new EdmondsKarp().maxFlow(work, currentS, currentT, seq);
                            return new PreRunResult(seq, f, "Edmonds-Karp");
                        case "Ford-Fulkerson":
                            f = new FordFulkerson().maxFlow(work, currentS, currentT, seq);
                            return new PreRunResult(seq, f, "Ford-Fulkerson");
                        case "Dinic":
                            f = new Dinic().maxFlow(work, currentS, currentT, seq);
                            return new PreRunResult(seq, f, "Dinic");
                        case "Goldberg-Tarjan":
                            f = new GoldbergTarjan().maxFlow(work, currentS, currentT, seq);
                            return new PreRunResult(seq, f, "Goldberg-Tarjan");
                        default:
                            return new PreRunResult(new ArrayList<>(), -1, algo);
                    }
                } finally {
                    long t1 = System.currentTimeMillis();
                    publish(String.format("%s Pre-run time %d ms", algo, (t1 - t0)));
                }
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks) appendLog(s + "\n");
            }

            @Override
            protected void done() {
                try {
                    PreRunResult r = get();
                    frames = r.seq;
                    frameIdx = -1;
                    canvas.clearFrame();

                    appendLog(String.format("%s Pre-run completed：Frames=%d，Maximum flow=%d\n",
                            r.algoName, frames.size(), r.maxflow));

                    if (frames.isEmpty()) {
                        appendLog("Note: This pre-run produced no frames (small graph or algorithm-specific corner case).\n");
                    }
                } catch (Exception ex) {
                    appendLog("Pre-run failed：" + ex.getMessage() + "\n");
                    frames = new ArrayList<>();
                    frameIdx = -1;
                    canvas.clearFrame();
                } finally {
                    setControlsEnabled(true);
                }
            }
        }.execute();
    }

    /**
     * Enables/disables all UI controls during background work.
     * Also keeps the auto-play button label consistent with the current player state.
     */
    private void setControlsEnabled(boolean enabled) {
        algorithmSelect.setEnabled(enabled);
        btnGenerateGraph.setEnabled(enabled);
        btnStep.setEnabled(enabled);
        btnAutoPlay.setEnabled(enabled);
        btnReset.setEnabled(enabled);
        speedSlider.setEnabled(enabled);
        btnRunTestEnv.setEnabled(enabled);
        btnSaveLog.setEnabled(enabled);
        btnClearLog.setEnabled(enabled);
        spinnerN.setEnabled(enabled);
        spinnerMaxCap.setEnabled(enabled);

        if (enabled && player.isRunning()) {
            btnAutoPlay.setText("Pause");
        } else if (enabled) {
            btnAutoPlay.setText("Auto-play");
        }
    }

    /**
     * Advances playback by one frame and updates the canvas and log.
     * When the last frame is reached, auto-play is stopped.
     */
    private void stepOnce() {
        if (frameIdx + 1 < frames.size()) {
            frameIdx++;
            canvas.setFrame(frames.get(frameIdx));
            appendLog("frame " + frameIdx + "/" + (frames.size() - 1) + "\n");

            if (frameIdx == frames.size() - 1) {
                appendLog("The saturated cut has been marked.\n");
                player.stop();
                btnAutoPlay.setText("Auto-play");
            }
        } else {
            player.stop();
            btnAutoPlay.setText("Auto-play");
        }
    }

    /**
     * Toggles auto-play on/off. If already at the last frame, resets playback to the start first.
     */
    private void toggleAuto() {
        if (player.isRunning()) {
            player.stop();
            btnAutoPlay.setText("Auto-play");
        } else {
            if (frames.isEmpty()) {
                appendLog("No frame sequence yet. Please generate a graph and pre-run an algorithm first.\n");
                return;
            }
            if (frameIdx + 1 >= frames.size()) resetToStart();
            player.start();
            btnAutoPlay.setText("Pause");
        }
    }

    /**
     * Replaces the current frame sequence and resets playback to the start.
     * Intended for external callers that want to provide a new precomputed sequence.
     */
    private void resetPlayback(List<VisFrame> seq) {
        player.stop();
        btnAutoPlay.setText("Auto-play");
        frames = (seq == null) ? new ArrayList<>() : seq;
        frameIdx = -1;
        canvas.clearFrame();
    }

    /**
     * Resets only the playback cursor and highlights, keeping the current graph and frame list.
     */
    private void resetToStart() {
        player.stop();
        btnAutoPlay.setText("Auto-play");
        frameIdx = -1;
        canvas.clearFrame();
        appendLog("Reset to the first step (highlights cleared; the current graph and frame sequence are kept).\n");
    }

    /**
     * Shows a dialog to run either a quick test batch or a longer report-generating batch.
     * The test is executed in a {@link SwingWorker} to keep the UI responsive.
     */
    private void chooseAndRunTestEnv() {
        Object[] options = {"Short test", "Long test (generate report)", "Cancel"};
        int choice = JOptionPane.showOptionDialog(
                this,
                "Please select the test environment version to run：\n"
                        + "• Short test\n"
                        + "• Long test (generate report)",
                "Run the test environment",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );
        if (choice == 0) {
            runTestEnvQuickAsync();
        } else if (choice == 1) {
            runTestEnvFullAsync();
        }
    }

    /**
     * Runs the "short" test suite in the background and prints the report to the log area.
     */
    private void runTestEnvQuickAsync() {
        setControlsEnabled(false);
        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() {
                logArea.setText("");

                publish("Student: Bo Fu");
                publish("Matrikelnummer: 3820106");
                publish("Kurs: Effiziente Graphenalgorithmen (EGA) 2025/26");
                publish("Datum: " + java.time.LocalDate.now());
                publish("==============================================\n");
                publish("=== EGA Max-Flow Test Report (GUI quick run) ===");

                List<TestEnvironment.BatchConfig> batches = new ArrayList<>();
                batches.add(new TestEnvironment.BatchConfig(3, 20, 50));
                batches.add(new TestEnvironment.BatchConfig(2, 40, 50));

                TestEnvironment.runBatches(
                        batches,
                        new Random(),
                        line -> publish(line)
                );

                publish("=== Test environment quick run finished. ===");
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks) appendLog(s + "\n");
            }

            @Override
            protected void done() {
                setControlsEnabled(true);
            }
        }.execute();
    }

    /**
     * Runs the "full" (longer) test suite in the background and prints a detailed report to the log area.
     */
    private void runTestEnvFullAsync() {
        setControlsEnabled(false);
        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() {
                logArea.setText("");

                publish("Student: Bo Fu");
                publish("Matrikelnummer: 3820106");
                publish("Kurs: Effiziente Graphenalgorithmen (EGA) 2025/26");
                publish("Datum: " + java.time.LocalDate.now());
                publish("==============================================\n");
                publish("=== EGA Max-Flow Test Report (GUI full run) ===");

                List<TestEnvironment.BatchConfig> batches = new ArrayList<>();
                batches.add(new TestEnvironment.BatchConfig(20, 50, 100));
                batches.add(new TestEnvironment.BatchConfig(15, 80, 100));
                batches.add(new TestEnvironment.BatchConfig(10, 120, 100));

                TestEnvironment.runBatches(
                        batches,
                        new Random(),
                        line -> publish(line)
                );

                publish("=== Full test run finished. ===");
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks) appendLog(s + "\n");
            }

            @Override
            protected void done() {
                setControlsEnabled(true);
            }
        }.execute();
    }

    /**
     * Appends text to the log area and scrolls to the end.
     */
    private void appendLog(String msg) {
        logArea.append(msg);
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    /**
     * Exports the current log content into a user-chosen .txt file.
     */
    private void saveLogToFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save the test report");

        int result = fc.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            java.io.File file = fc.getSelectedFile();
            if (!file.getName().contains(".")) {
                file = new java.io.File(file.getParentFile(), file.getName() + ".txt");
            }
            try (java.io.FileWriter fw = new java.io.FileWriter(file)) {
                fw.write(logArea.getText());
                appendLog("\nThe report has been saved to: " + file.getAbsolutePath() + "\n");
            } catch (java.io.IOException ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Error writing file:\n" + ex.getMessage(),
                        "Save failed",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainWindow win = new MainWindow();
            win.setVisible(true);
        });
    }

    /**
     * Result container for one algorithm pre-run.
     */
    private static class PreRunResult {
        final List<VisFrame> seq;
        final long maxflow;
        final String algoName;

        PreRunResult(List<VisFrame> seq, long maxflow, String algoName) {
            this.seq = seq;
            this.maxflow = maxflow;
            this.algoName = algoName;
        }
    }
}
