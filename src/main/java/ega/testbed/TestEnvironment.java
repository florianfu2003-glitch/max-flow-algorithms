package ega.testbed;

import ega.algorithms.Dinic;
import ega.algorithms.EdmondsKarp;
import ega.algorithms.FordFulkerson;
import ega.algorithms.GoldbergTarjan;
import ega.core.Edge;
import ega.core.FlowValidators;
import ega.core.Graph;
import ega.generator.GraphGenerator;

import java.util.*;
import java.util.function.Consumer;

/**
 * Test harness for randomized max-flow instances.
 *
 * <p>Responsibilities (aligned with typical course-project requirements):
 * <ul>
 *   <li>Generate batches of random max-flow instances (generation is delegated to {@link GraphGenerator}).</li>
 *   <li>For each instance, run multiple max-flow algorithms on independent clones of the same input graph.</li>
 *   <li>Validate each produced flow with:
 *     <ul>
 *       <li>capacity constraints</li>
 *       <li>flow conservation</li>
 *       <li>existence of a saturated s-t cut in the final residual network</li>
 *     </ul>
 *   </li>
 *   <li>Check whether all algorithms agree on the maximum flow value.</li>
 *   <li>Perform destructive sanity checks to demonstrate that the validators can detect violations:
 *     <ul>
 *       <li>Sanity A: force a flow overflow on an original edge (flow &gt; origCap).</li>
 *       <li>Sanity B: break the saturated-cut property by injecting residual capacity across the final cut.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>Output:
 * <ul>
 *   <li>Can print directly to {@code System.out}, or</li>
 *   <li>write to a {@link Consumer} callback (used by the GUI).</li>
 * </ul>
 */
public class TestEnvironment {

    /**
     * One batch configuration triple: (numInstances, n, maxCap).
     */
    public static class BatchConfig {
        public final int numInstances;
        public final int n;
        public final int maxCap;

        public BatchConfig(int numInstances, int n, int maxCap) {
            this.numInstances = numInstances;
            this.n = n;
            this.maxCap = maxCap;
        }
    }

    /**
     * Aggregated report for one algorithm on one instance.
     *
     * <p>We store both the numeric outcome (maxFlow) and the validator results. If the algorithm
     * throws an exception, {@code ranOK=false} and other checks are forced to false to avoid
     * misleading "OK" flags.
     */
    private static class AlgoReport {
        String algoname;
        long maxFlow;

        boolean capacityOK;
        boolean conservationOK;
        boolean saturatedCutOK;

        boolean ranOK;
        String errorMessage;
    }

    /* ===================== Public entry points (two overloads) ===================== */

    /**
     * Convenience entry that prints to the console.
     */
    public static void runBatches(List<BatchConfig> batches, Random rng) {
        runBatches(batches, rng, System.out::println);
    }

    /**
     * Main entry point with a log callback (used by the GUI).
     *
     * @param batches batch configurations
     * @param rng     random generator (used for instance generation)
     * @param log     consumer that receives one line at a time; if null, falls back to System.out
     */
    public static void runBatches(List<BatchConfig> batches, Random rng, Consumer<String> log) {
        if (log == null) log = System.out::println;

        log.accept("=== EGA Max-Flow Test Report ===");
        log.accept("Total batches: " + batches.size());
        log.accept("--------------------------------");

        int globalInstanceId = 1;

        for (BatchConfig batch : batches) {
            log.accept("");
            log.accept(">>> Batch: " + batch.numInstances + " instances, n=" + batch.n + ", maxCap=" + batch.maxCap);
            log.accept("------------------------------------------");

            int mismatchCnt = 0; // number of instances where algorithms disagree on max flow
            int anyFailCnt  = 0; // number of instances where any check failed (or any algorithm crashed)
            int sanityOKCnt = 0; // number of instances where at least one sanity check was detected

            for (int instIdx = 0; instIdx < batch.numInstances; instIdx++, globalInstanceId++) {

                // 1) Generate a random instance.
                GraphGenerator.Result gen = GraphGenerator.generate(batch.n, batch.maxCap, rng);
                Graph original = gen.graph;
                int s = gen.s, t = gen.t;

                // 2) Run each algorithm on an independent clone and validate its output.
                Map<String, AlgoReport> reports = new LinkedHashMap<>();

                reports.put("Ford-Fulkerson", runAndValidate("Ford-Fulkerson", original, s, t,
                        g -> new FordFulkerson().maxFlow(g, s, t)));

                reports.put("Edmonds-Karp", runAndValidate("Edmonds-Karp", original, s, t,
                        g -> new EdmondsKarp().maxFlow(g, s, t)));

                reports.put("Dinic", runAndValidate("Dinic", original, s, t,
                        g -> new Dinic().maxFlow(g, s, t)));

                reports.put("Goldberg-Tarjan", runAndValidate("Goldberg-Tarjan", original, s, t,
                        g -> new GoldbergTarjan().maxFlow(g, s, t)));

                // 3) Agreement check (ignore algorithms that crashed).
                long ref = Long.MIN_VALUE;
                boolean haveRef = false;
                boolean allAgree = true;

                for (AlgoReport ar : reports.values()) {
                    if (!ar.ranOK) continue;
                    if (!haveRef) {
                        ref = ar.maxFlow;
                        haveRef = true;
                    } else if (ar.maxFlow != ref) {
                        allAgree = false;
                    }
                }
                if (!haveRef) {
                    // Extremely unlikely: all algorithms failed.
                    allAgree = false;
                }

                // 4) Print instance report.
                log.accept("Instance #" + globalInstanceId + " (idxInBatch=" + instIdx
                        + ", n=" + batch.n + ", maxCap=" + batch.maxCap + "):");

                StringBuilder line = new StringBuilder("  maxFlow FF/EK/Dinic/GT = ");
                line.append(fmtFlow(reports.get("Ford-Fulkerson"))).append("/")
                        .append(fmtFlow(reports.get("Edmonds-Karp"))).append("/")
                        .append(fmtFlow(reports.get("Dinic"))).append("/")
                        .append(fmtFlow(reports.get("Goldberg-Tarjan")));
                line.append(allAgree ? " [OK]" : " [MISMATCH]");
                log.accept(line.toString());

                printAlgoReport(log, "    ", reports.get("Ford-Fulkerson"));
                printAlgoReport(log, "    ", reports.get("Edmonds-Karp"));
                printAlgoReport(log, "    ", reports.get("Dinic"));
                printAlgoReport(log, "    ", reports.get("Goldberg-Tarjan"));

                if (!allAgree) {
                    mismatchCnt++;
                    log.accept("    >>> WARNING: algorithms disagree on max flow value!");
                }

                boolean anyFail = reports.values().stream().anyMatch(
                        r -> !r.ranOK || !(r.capacityOK && r.conservationOK && r.saturatedCutOK));
                if (anyFail) anyFailCnt++;

                // 5) Destructive sanity checks on a "reasonable solved state".
                //    We first compute a max-flow using Edmonds–Karp, then intentionally corrupt the result.
                Graph solved = original.cloneGraph();
                new EdmondsKarp().maxFlow(solved, s, t);

                boolean detectedA = sanityFlowOverflow(solved, s, t);
                boolean detectedB = sanityBreakSaturatedCut(solved, s, t);

                boolean detectedAny = detectedA || detectedB;
                if (detectedAny) sanityOKCnt++;

                log.accept("    Sanity A (flow overflow): " + (detectedA ? "detected violation" : "NOT detected"));
                log.accept("    Sanity B (unsaturated cut): " + (detectedB ? "detected violation" : "NOT detected"));
                log.accept("");
            }

            // 6) Batch summary.
            log.accept(String.format(Locale.ROOT,
                    "Batch summary: mismatched flows=%d / any check failed=%d / sanity OK=%d",
                    mismatchCnt, anyFailCnt, sanityOKCnt));
        }

        log.accept("=== End of Report ===");
    }

    /* ===================== Convenience wrappers (kept for compatibility) ===================== */

    /**
     * Convenience wrapper for running exactly one batch.
     */
    public static void runBatch(int count, int n, int maxCap, Random rng) {
        List<BatchConfig> list = new ArrayList<>();
        list.add(new BatchConfig(count, n, maxCap));
        runBatches(list, rng);
    }

    /**
     * Small quick demo (few small instances).
     */
    public static void runSmallDemo() {
        List<BatchConfig> demo = new ArrayList<>();
        demo.add(new BatchConfig(3, 20, 50));
        demo.add(new BatchConfig(2, 40, 50));
        runBatches(demo, new Random(42));
    }

    /**
     * Larger run (intended for producing a longer report).
     */
    public static void runBigReport() {
        List<BatchConfig> big = new ArrayList<>();
        big.add(new BatchConfig(20, 50, 100));
        big.add(new BatchConfig(15, 80, 100));
        big.add(new BatchConfig(10, 120, 100));
        runBatches(big, new Random(42));
    }

    /* ===================== Internal helpers ===================== */

    /**
     * Minimal functional interface so we can wrap different solvers consistently.
     */
    private interface Solver {
        long solve(Graph g) throws Exception;
    }

    /**
     * Clone -> solve -> validate, with exception containment.
     */
    private static AlgoReport runAndValidate(String name, Graph base, int s, int t, Solver solver) {
        AlgoReport r = new AlgoReport();
        r.algoname = name;

        try {
            Graph g = base.cloneGraph();
            long flow = solver.solve(g);

            r.maxFlow = flow;
            r.capacityOK = FlowValidators.capacityConstraints(g);
            r.conservationOK = FlowValidators.flowConservation(g, s, t);
            r.saturatedCutOK = FlowValidators.saturatedCutExists(g, s, t);
            r.ranOK = true;

        } catch (Throwable ex) {
            r.ranOK = false;
            r.errorMessage = ex.getClass().getSimpleName() + ": " + ex.getMessage();

            // If the solver crashed, mark checks as false to avoid implying correctness.
            r.capacityOK = false;
            r.conservationOK = false;
            r.saturatedCutOK = false;
            r.maxFlow = Long.MIN_VALUE;
        }

        return r;
    }

    /**
     * Prints a per-algorithm summary (with indentation).
     */
    private static void printAlgoReport(Consumer<String> log, String indent, AlgoReport r) {
        log.accept(indent + r.algoname + (r.ranOK ? ":" : " [FAILED]: " + r.errorMessage));
        log.accept(indent + "  capacity constraints: " + (r.capacityOK ? "OK" : "FAIL"));
        log.accept(indent + "  flow conservation:    " + (r.conservationOK ? "OK" : "FAIL"));
        log.accept(indent + "  saturated cut exists: " + (r.saturatedCutOK ? "OK" : "FAIL"));
    }

    /**
     * Pretty-prints the max-flow value; uses 'X' if the algorithm crashed.
     */
    private static String fmtFlow(AlgoReport r) {
        return (r != null && r.ranOK) ? String.valueOf(r.maxFlow) : "X";
    }

    /**
     * Sanity A:
     * Corrupt a solved instance by forcing one original edge's flow to exceed its original capacity.
     *
     * <p>Expected: capacity constraints and/or flow conservation should fail.
     *
     * @return true iff at least one validator detects a violation
     */
    private static boolean sanityFlowOverflow(Graph g, int s, int t) {
        // Corrupt the first original forward edge we encounter.
        outer:
        for (int u = 0; u < g.size(); u++) {
            for (Edge e : g.adj(u)) {
                if (e.origCap > 0) {
                    e.flow = e.origCap + 123;
                    break outer;
                }
            }
        }

        boolean capOK = FlowValidators.capacityConstraints(g);
        boolean consOK = FlowValidators.flowConservation(g, s, t);
        boolean cutOK = FlowValidators.saturatedCutExists(g, s, t);

        // If everything still looks OK, the corruption was not detected (which would be bad).
        return !(capOK && consOK && cutOK);
    }

    /**
     * Sanity B:
     * Break the saturated-cut certificate after a max-flow is computed.
     *
     * <p>Procedure:
     * <ol>
     *   <li>Compute S = vertices reachable from s in the residual network via edges with cap &gt; 0.</li>
     *   <li>Find an original edge crossing from S to V\\S that is saturated (cap == 0) and set its
     *       residual capacity to 1, which invalidates the saturated-cut property.</li>
     * </ol>
     *
     * <p>Expected: {@link FlowValidators#saturatedCutExists(Graph, int, int)} returns false.
     *
     * @return true iff the cut validator detects the injected violation (or any validator fails)
     */
    private static boolean sanityBreakSaturatedCut(Graph g, int s, int t) {
        boolean[] inS = residualReachable(g, s);

        // Prefer: pick a saturated original edge crossing S -> T and set residual cap to 1.
        boolean changed = false;
        for (int u = 0; u < g.size() && !changed; u++) {
            if (!inS[u]) continue;
            for (Edge e : g.adj(u)) {
                int v = e.to;
                if (e.origCap <= 0) continue; // only original directed edges
                if (v < 0 || v >= g.size()) continue;
                if (inS[v]) continue;         // must cross the cut
                if (e.cap == 0) {
                    e.cap = 1;
                    changed = true;
                    break;
                }
            }
        }

        // Fallback: if no strictly saturated crossing edge exists, bump any crossing edge.
        if (!changed) {
            for (int u = 0; u < g.size() && !changed; u++) {
                if (!inS[u]) continue;
                for (Edge e : g.adj(u)) {
                    if (e.origCap <= 0) continue;
                    int v = e.to;
                    if (!inS[v]) {
                        e.cap = Math.max(1, (int) e.cap + 1);
                        changed = true;
                        break;
                    }
                }
            }
        }

        boolean capOK = FlowValidators.capacityConstraints(g);
        boolean consOK = FlowValidators.flowConservation(g, s, t);
        boolean cutOK = FlowValidators.saturatedCutExists(g, s, t);

        // Success criterion: the saturated-cut validator should fail (or at least some validator fails).
        return !cutOK || !(capOK && consOK && cutOK);
    }

    /**
     * BFS reachability in the residual network using only edges with cap > 0.
     *
     * @param g residual network
     * @param s source
     * @return vis[v] == true iff v is reachable from s in the residual network
     */
    private static boolean[] residualReachable(Graph g, int s) {
        boolean[] vis = new boolean[g.size()];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(s);
        vis[s] = true;

        while (!q.isEmpty()) {
            int u = q.poll();
            for (Edge e : g.adj(u)) {
                if (e.cap > 0 && !vis[e.to]) {
                    vis[e.to] = true;
                    q.add(e.to);
                }
            }
        }
        return vis;
    }
}
