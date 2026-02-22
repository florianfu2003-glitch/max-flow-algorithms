package ega.algorithms;

import ega.core.Edge;
import ega.core.Graph;
import ega.gui.vis.*;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

/**
 * Goldberg–Tarjan Push–Relabel algorithm (preflow-push) for computing a maximum s-t flow.
 *
 * <p>Core invariants:
 * <ul>
 *   <li>We maintain a <b>preflow</b>: flow conservation may be violated at intermediate vertices.
 *       The violation is tracked as {@code excess[v]} (net inflow minus outflow).</li>
 *   <li>Each vertex has an integer <b>height/label</b> {@code height[v]}.
 *       Pushes are only allowed on <b>admissible</b> edges (u->v) with
 *       {@code residualCap(u,v) > 0} and {@code height[u] == height[v] + 1}.</li>
 *   <li>When no admissible edge exists for an active vertex u, we <b>relabel</b> u to
 *       {@code 1 + min{ height[v] | (u->v) has residual capacity > 0 }}.</li>
 * </ul>
 *
 * <p>Implementation choices:
 * <ul>
 *   <li>Active vertices (excess>0, excluding s and t) are processed in a FIFO queue.</li>
 *   <li>Current-arc optimization via {@code ptr[u]} avoids rescanning adjacency lists from scratch.</li>
 *   <li>The residual network is stored in-place: {@code e.cap} is residual capacity and each edge has a reverse edge {@code e.rev}.</li>
 * </ul>
 *
 * <p>Visualization support:
 * If {@code out} is provided, the algorithm emits frames for levels/heights, pushes, relabels, and a residual cut.
 */
public class GoldbergTarjan {

    /** Vertex heights (labels). */
    private int[] height;

    /** Excess flow at each vertex (may be negative at s due to initialization). */
    private long[] excess;

    /** FIFO queue of active vertices (excluding s and t). */
    private Queue<Integer> activeQ;

    /** Whether a vertex is currently in {@link #activeQ}. */
    private boolean[] inQ;

    /** Current-arc pointer per vertex for discharge scanning. */
    private int[] ptr;

    /** Visualization output (may be null). */
    private List<VisFrame> out;

    /**
     * If true, overlay the current residual cut together with other events in the same frame.
     * If false, emit the cut as a separate frame after the event.
     */
    private boolean overlayCutInSameFrame = true;

    public void setOverlayCutInSameFrame(boolean enable) {
        this.overlayCutInSameFrame = enable;
    }

    /**
     * Computes the maximum flow from {@code s} to {@code t} (no visualization).
     *
     * @param g residual network representation (modified in-place)
     * @param s source node index
     * @param t sink node index
     * @return maximum s-t flow value
     */
    public long maxFlow(Graph g, int s, int t) {
        final int n = g.size();
        height = new int[n];
        excess = new long[n];
        activeQ = new ArrayDeque<>();
        inQ = new boolean[n];
        ptr = new int[n];

        // Initialize preflow: set height[s]=n and saturate all outgoing residual edges of s.
        height[s] = n;
        for (Edge e : g.adj(s)) {
            if (e.cap <= 0) continue;

            long send = e.cap;
            Edge rev = g.adj(e.to).get(e.rev);

            // Push full residual capacity out of s.
            e.cap -= send;
            rev.cap += send;

            e.flow += send;
            rev.flow -= send;

            excess[s] -= send;
            excess[e.to] += send;

            // Activate newly overflowing vertices (excluding s and t).
            if (e.to != s && e.to != t && !inQ[e.to] && excess[e.to] > 0) {
                activeQ.add(e.to);
                inQ[e.to] = true;
            }
        }

        // Process active vertices until none remain.
        while (!activeQ.isEmpty()) {
            int u = activeQ.poll();
            inQ[u] = false;

            if (u == s || u == t) continue;

            discharge(g, u, s, t);

            // If u still has excess, keep it active.
            if (excess[u] > 0) {
                activeQ.add(u);
                inQ[u] = true;
            }
        }

        // With a valid preflow and no active vertices, the excess at t equals the max flow value.
        return excess[t];
    }

    /**
     * Computes the maximum flow from {@code s} to {@code t}, optionally emitting visualization frames.
     *
     * @param g   residual network representation (modified in-place)
     * @param s   source node index
     * @param t   sink node index
     * @param out list to append visualization frames to; may be null to disable visualization
     * @return maximum s-t flow value
     */
    public long maxFlow(Graph g, int s, int t, List<VisFrame> out) {
        this.out = out;

        final int n = g.size();
        height = new int[n];
        excess = new long[n];
        activeQ = new ArrayDeque<>();
        inQ = new boolean[n];
        ptr = new int[n];

        // Initialize preflow (visualized): height[s]=n and saturate s's outgoing edges.
        height[s] = n;
        addLevelsAndMaybeCut(g, s, height);

        for (Edge e : g.adj(s)) {
            if (e.cap <= 0) continue;

            long send = e.cap;
            Edge rev = g.adj(e.to).get(e.rev);

            e.cap -= send;
            rev.cap += send;

            e.flow += send;
            rev.flow -= send;

            excess[s] -= send;
            excess[e.to] += send;

            addPushAndMaybeCut(g, s, s, e.to, send);

            if (e.to != s && e.to != t && !inQ[e.to] && excess[e.to] > 0) {
                activeQ.add(e.to);
                inQ[e.to] = true;
            }
        }

        // Main loop: FIFO discharge of active vertices.
        while (!activeQ.isEmpty()) {
            int u = activeQ.poll();
            inQ[u] = false;

            if (u == s || u == t) continue;

            dischargeVis(g, u, s, t);

            if (excess[u] > 0) {
                activeQ.add(u);
                inQ[u] = true;
            }
        }

        // Ensure the last frame is a cut frame (useful for the UI).
        forceFinalCutFrame(g, s);

        this.out = null;
        return excess[t];
    }

    /**
     * Push operation (visual version): sends flow on an admissible residual edge u->v.
     * Also activates v if it becomes active (excess>0, excluding s and t).
     */
    private void pushVis(Graph g, int u, Edge e, int s, int t) {
        long send = Math.min(excess[u], e.cap);
        if (send <= 0) return;

        Edge rev = g.adj(e.to).get(e.rev);

        e.cap -= send;
        rev.cap += send;

        e.flow += send;
        rev.flow -= send;

        excess[u] -= send;
        excess[e.to] += send;

        addPushAndMaybeCut(g, s, u, e.to, send);

        if (e.to != s && e.to != t && !inQ[e.to] && excess[e.to] > 0) {
            activeQ.add(e.to);
            inQ[e.to] = true;
        }
    }

    /**
     * Relabel operation (visual version): raises height[u] so that at least one outgoing
     * residual edge becomes admissible (if any residual edge exists).
     */
    private void relabelVis(Graph g, int u, int s) {
        int minH = Integer.MAX_VALUE;
        for (Edge e : g.adj(u)) {
            if (e.cap > 0) minH = Math.min(minH, height[e.to]);
        }
        if (minH == Integer.MAX_VALUE) return; // No outgoing residual edge; height stays unchanged.

        int oldH = height[u];
        int newH = minH + 1;
        height[u] = newH;

        addRelabelLevelsAndMaybeCut(g, s, u, oldH, newH);
    }

    /**
     * Discharge (visual version): repeatedly pushes along admissible edges until u has no excess.
     * If no admissible edge exists, relabel u and restart scanning from the beginning.
     */
    private void dischargeVis(Graph g, int u, int s, int t) {
        while (excess[u] > 0) {
            if (ptr[u] >= g.adj(u).size()) {
                relabelVis(g, u, s);
                ptr[u] = 0;
                continue;
            }

            Edge e = g.adj(u).get(ptr[u]);
            if (e.cap > 0 && height[u] == height[e.to] + 1) {
                pushVis(g, u, e, s, t);
            } else {
                ptr[u]++;
            }
        }
    }

    /**
     * Push operation (non-visual version).
     */
    private void push(Graph g, int u, Edge e, int s, int t) {
        long send = Math.min(excess[u], e.cap);
        if (send <= 0) return;

        Edge rev = g.adj(e.to).get(e.rev);

        e.cap -= send;
        rev.cap += send;

        e.flow += send;
        rev.flow -= send;

        excess[u] -= send;
        excess[e.to] += send;

        if (e.to != s && e.to != t && !inQ[e.to] && excess[e.to] > 0) {
            activeQ.add(e.to);
            inQ[e.to] = true;
        }
    }

    /**
     * Relabel operation (non-visual version).
     */
    private void relabel(Graph g, int u) {
        int minH = Integer.MAX_VALUE;
        for (Edge e : g.adj(u)) {
            if (e.cap > 0) minH = Math.min(minH, height[e.to]);
        }
        // Assumes relabel is only called when there exists at least one outgoing residual edge.
        height[u] = minH + 1;
    }

    /**
     * Discharge (non-visual version).
     */
    private void discharge(Graph g, int u, int s, int t) {
        while (excess[u] > 0) {
            if (ptr[u] >= g.adj(u).size()) {
                relabel(g, u);
                ptr[u] = 0;
                continue;
            }

            Edge e = g.adj(u).get(ptr[u]);
            if (e.cap > 0 && height[u] == height[e.to] + 1) {
                push(g, u, e, s, t);
            } else {
                ptr[u]++;
            }
        }
    }

    /**
     * Emits a Levels frame, and optionally overlays (or follows up with) the current residual cut.
     */
    private void addLevelsAndMaybeCut(Graph g, int s, int[] h) {
        if (out == null) return;

        VisFrame f1 = new VisFrame();
        f1.add(new Levels(h.clone()));

        if (overlayCutInSameFrame) {
            f1.add(new Cut(residualReachable(g, s)));
            out.add(f1);
        } else {
            out.add(f1);
            VisFrame f2 = new VisFrame();
            f2.add(new Cut(residualReachable(g, s)));
            out.add(f2);
        }
    }

    /**
     * Emits a Push frame, and optionally overlays (or follows up with) the current residual cut.
     */
    private void addPushAndMaybeCut(Graph g, int s, int u, int v, long delta) {
        if (out == null) return;

        VisFrame f1 = new VisFrame();
        f1.add(new Push(u, v, delta));

        if (overlayCutInSameFrame) {
            f1.add(new Cut(residualReachable(g, s)));
            out.add(f1);
        } else {
            out.add(f1);
            VisFrame f2 = new VisFrame();
            f2.add(new Cut(residualReachable(g, s)));
            out.add(f2);
        }
    }

    /**
     * Emits a Relabel frame plus updated Levels, and optionally overlays (or follows up with) the current residual cut.
     */
    private void addRelabelLevelsAndMaybeCut(Graph g, int s, int u, int oldH, int newH) {
        if (out == null) return;

        VisFrame f1 = new VisFrame();
        f1.add(new Relabel(u, oldH, newH));
        f1.add(new Levels(height.clone()));

        if (overlayCutInSameFrame) {
            f1.add(new Cut(residualReachable(g, s)));
            out.add(f1);
        } else {
            out.add(f1);
            VisFrame f2 = new VisFrame();
            f2.add(new Cut(residualReachable(g, s)));
            out.add(f2);
        }
    }

    /**
     * Appends a final frame that contains only the residual cut (no deduplication).
     */
    private void forceFinalCutFrame(Graph g, int s) {
        if (out == null) return;

        VisFrame f = new VisFrame();
        f.add(new Cut(residualReachable(g, s)));
        out.add(f);
    }

    /**
     * Computes residual reachability from {@code s} by BFS using only edges with {@code cap > 0}.
     * The reachable set defines the S-side of an s-t minimum cut in the final residual network.
     */
    private boolean[] residualReachable(Graph g, int s) {
        int n = g.size();
        boolean[] vis = new boolean[n];
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
