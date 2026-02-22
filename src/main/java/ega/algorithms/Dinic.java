package ega.algorithms;

import ega.core.Edge;
import ega.core.Graph;
import ega.gui.vis.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

/**
 * Dinic's algorithm for the maximum s-t flow problem.
 *
 * <p>Assumptions about the underlying graph representation:
 * <ul>
 *   <li>The graph is directed and stored as adjacency lists.</li>
 *   <li>Each forward edge has a corresponding reverse edge, referenced by {@code e.rev}.</li>
 *   <li>{@code e.cap} stores the current residual capacity of the edge (updated in-place).</li>
 *   <li>{@code e.flow} stores the current flow value on the edge (reverse edges may carry negative flow).</li>
 * </ul>
 *
 * <p>High-level structure:
 * <ol>
 *   <li>Build a level graph with BFS on the residual network.</li>
 *   <li>On that level graph, repeatedly send blocking flows using DFS with the current-arc optimization.</li>
 *   <li>Repeat until the sink is unreachable in the residual network.</li>
 * </ol>
 */
public class Dinic {

    /**
     * Computes the maximum flow from {@code s} to {@code t}.
     *
     * @param g residual network representation (will be modified in-place)
     * @param s source node index
     * @param t sink node index
     * @return maximum s-t flow value
     */
    public long maxFlow(Graph g, int s, int t) {
        long flow = 0L;
        final int n = g.size();

        // level[v] = BFS distance from s in the residual network (unreachable => -1).
        int[] level = new int[n];
        // ptr[v] = "current arc" pointer for DFS in the current BFS phase.
        int[] ptr = new int[n];

        while (buildLevelGraph(g, s, t, level)) {
            Arrays.fill(ptr, 0);

            // Augment within this level graph until it becomes blocking.
            while (true) {
                long pushed = dfsBlockingFlow(g, s, t, Long.MAX_VALUE / 4, level, ptr);
                if (pushed == 0) break;
                flow += pushed;
            }
        }
        return flow;
    }

    /**
     * Computes the maximum flow from {@code s} to {@code t}, and optionally records visualization frames.
     *
     * <p>If {@code out} is non-null, this method appends frames that describe:
     * <ul>
     *   <li>the BFS levels after each successful BFS,</li>
     *   <li>each found augmenting path in the level graph,</li>
     *   <li>each push operation along that path,</li>
     *   <li>and finally a minimum cut induced by final residual reachability from {@code s}.</li>
     * </ul>
     *
     * @param g   residual network representation (will be modified in-place)
     * @param s   source node index
     * @param t   sink node index
     * @param out list to append visualization frames to; may be null to disable visualization
     * @return maximum s-t flow value
     */
    public long maxFlow(Graph g, int s, int t, List<VisFrame> out) {
        long flow = 0L;
        final int n = g.size();

        int[] level = new int[n];
        int[] ptr = new int[n];

        while (buildLevelGraph(g, s, t, level)) {

            if (out != null) {
                int[] copy = Arrays.copyOf(level, level.length);
                VisFrame f = new VisFrame();
                f.add(new Levels(copy));
                out.add(f);
            }

            Arrays.fill(ptr, 0);

            while (true) {
                ArrayList<Integer> pathStack = new ArrayList<>();
                long pushed = dfsBlockingFlowVis(g, s, t, Long.MAX_VALUE / 4, level, ptr, pathStack);

                if (pushed == 0) break;

                flow += pushed;

                if (out != null) {
                    int[] nodes = new int[pathStack.size()];
                    for (int i = 0; i < nodes.length; i++) nodes[i] = pathStack.get(i);

                    VisFrame pf = new VisFrame();
                    pf.add(new Path(nodes));
                    out.add(pf);

                    VisFrame qf = new VisFrame();
                    for (int i = 0; i + 1 < nodes.length; i++) {
                        qf.add(new Push(nodes[i], nodes[i + 1], pushed));
                    }
                    out.add(qf);
                }
            }
        }

        // Reachable vertices from s in the final residual network define the S-side of an s-t minimum cut.
        if (out != null) {
            boolean[] inS = reachableFromS(g, s);
            VisFrame f = new VisFrame();
            f.add(new Cut(inS));
            out.add(f);
        }

        return flow;
    }

    /**
     * Builds the level graph by running BFS on the residual network.
     *
     * <p>Only edges with strictly positive residual capacity are traversed. The resulting levels satisfy:
     * {@code level[s] = 0} and for any traversed edge {@code (u -> v)} we set {@code level[v] = level[u] + 1}.
     *
     * @param g     residual network
     * @param s     source
     * @param t     sink
     * @param level output array; will be filled with BFS levels (unreachable => -1)
     * @return {@code true} iff {@code t} is reachable from {@code s} in the residual network
     */
    private static boolean buildLevelGraph(Graph g, int s, int t, int[] level) {
        Arrays.fill(level, -1);

        Queue<Integer> q = new ArrayDeque<>();
        level[s] = 0;
        q.add(s);

        while (!q.isEmpty()) {
            int u = q.poll();
            for (Edge e : g.adj(u)) {
                if (e.cap > 0 && level[e.to] == -1) {
                    level[e.to] = level[u] + 1;
                    q.add(e.to);
                }
            }
        }
        return level[t] != -1;
    }

    /**
     * DFS that sends flow in the current level graph (blocking-flow phase).
     *
     * <p>Uses the current-arc optimization via {@code ptr[]} so that, within one BFS phase,
     * each outgoing edge of each node is considered at most once (amortized).
     *
     * @param g      residual network
     * @param u      current node
     * @param t      sink
     * @param pushed upper bound on how much we are allowed to send along the current DFS prefix
     * @param level  BFS levels from {@link #buildLevelGraph}
     * @param ptr    current-arc pointers (modified in-place)
     * @return amount of flow actually sent; 0 if no augmenting route exists from {@code u} to {@code t}
     */
    private static long dfsBlockingFlow(Graph g, int u, int t, long pushed, int[] level, int[] ptr) {
        if (u == t) return pushed;

        while (ptr[u] < g.adj(u).size()) {
            Edge e = g.adj(u).get(ptr[u]);

            // Only follow edges that go "forward" in the level graph and have residual capacity.
            if (e.cap <= 0 || level[e.to] != level[u] + 1) {
                ptr[u]++;
                continue;
            }

            long canPush = dfsBlockingFlow(g, e.to, t, Math.min(pushed, e.cap), level, ptr);
            if (canPush > 0) {
                // Update residual capacities and flow values on forward and reverse edges.
                e.cap -= canPush;
                e.flow += canPush;

                Edge rev = g.adj(e.to).get(e.rev);
                rev.cap += canPush;
                rev.flow -= canPush;

                return canPush;
            }

            ptr[u]++;
        }

        // Pruning: u cannot reach t in the current level graph, treat it as unreachable for this BFS phase.
        level[u] = -1;
        return 0;
    }

    /**
     * Visualization-friendly variant of {@link #dfsBlockingFlow} that also maintains the current DFS path.
     *
     * <p>{@code pathStack} records the current recursion stack of nodes. On success (sink reached),
     * it contains the full augmenting path from {@code s} to {@code t}. On failure, it is rolled back
     * to the state it had when entering the current recursion frame.
     */
    private static long dfsBlockingFlowVis(Graph g,
                                           int u,
                                           int t,
                                           long pushed,
                                           int[] level,
                                           int[] ptr,
                                           ArrayList<Integer> pathStack) {
        pathStack.add(u);

        if (u == t) {
            return pushed;
        }

        int stackSizeBefore = pathStack.size();

        while (ptr[u] < g.adj(u).size()) {
            Edge e = g.adj(u).get(ptr[u]);

            if (e.cap <= 0 || level[e.to] != level[u] + 1) {
                ptr[u]++;
                continue;
            }

            long canPush = dfsBlockingFlowVis(g, e.to, t, Math.min(pushed, e.cap), level, ptr, pathStack);
            if (canPush > 0) {
                e.cap -= canPush;
                e.flow += canPush;

                Edge rev = g.adj(e.to).get(e.rev);
                rev.cap += canPush;
                rev.flow -= canPush;

                return canPush;
            }

            ptr[u]++;

            // Roll back any nodes added by the failed recursive attempt.
            while (pathStack.size() > stackSizeBefore) {
                pathStack.remove(pathStack.size() - 1);
            }
        }

        level[u] = -1;
        pathStack.remove(pathStack.size() - 1);
        return 0;
    }

    /**
     * Computes the set of nodes reachable from {@code s} in the final residual network
     * using only edges with {@code cap > 0}.
     *
     * <p>This set is commonly used to derive an s-t minimum cut: reachable nodes form the S-side.
     */
    private static boolean[] reachableFromS(Graph g, int s) {
        int n = g.size();
        boolean[] vis = new boolean[n];
        ArrayDeque<Integer> q = new ArrayDeque<>();

        vis[s] = true;
        q.add(s);

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
