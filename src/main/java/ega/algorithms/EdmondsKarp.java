package ega.algorithms;

import ega.core.Edge;
import ega.core.Graph;
import ega.gui.vis.*; // VisFrame / Path / Push / Cut

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

/**
 * Edmonds–Karp algorithm (Ford–Fulkerson with BFS) for computing a maximum s-t flow.
 *
 * <p>Key idea:
 * Repeatedly find a shortest (in number of edges) augmenting path in the residual network using BFS,
 * augment along it by the path bottleneck, and update residual capacities.
 *
 * <p>Complexity:
 * O(V * E^2) in general (with adjacency-list residual graph and BFS per augmentation).
 *
 * <p>Graph/edge assumptions (matching the project's residual representation):
 * <ul>
 *   <li>Edges are stored in adjacency lists {@code g.adj(u)}.</li>
 *   <li>{@code e.cap} is the current residual capacity (modified in-place).</li>
 *   <li>{@code e.flow} tracks the flow on the edge (reverse edges carry negative flow).</li>
 *   <li>{@code e.rev} is the index of the reverse edge in {@code g.adj(e.to)}.</li>
 * </ul>
 */
public class EdmondsKarp {

    /**
     * Computes the maximum flow from {@code s} to {@code t} (no visualization output).
     *
     * @param g residual network (modified in-place)
     * @param s source node index
     * @param t sink node index
     * @return maximum s-t flow value
     */
    public long maxFlow(Graph g, int s, int t) {
        if (s == t) return 0L;

        final long INF = Long.MAX_VALUE / 4; // large sentinel value; keeps headroom from overflow
        long flow = 0L;
        final int n = g.size();

        while (true) {
            // BFS on the residual network to find an augmenting path s -> t.
            int[] prevNode = new int[n]; // prevNode[v] = predecessor node on the BFS tree
            int[] prevEdge = new int[n]; // prevEdge[v] = index of the edge used to enter v from prevNode[v]
            Arrays.fill(prevNode, -1);
            prevNode[s] = s; // mark source as visited

            Queue<Integer> q = new ArrayDeque<>();
            q.add(s);

            boolean reachedT = false;
            BFS:
            while (!q.isEmpty()) {
                int u = q.poll();
                for (int i = 0; i < g.adj(u).size(); i++) {
                    Edge e = g.adj(u).get(i);

                    // Traverse only edges with positive residual capacity to unvisited nodes.
                    if (e.cap <= 0) continue;
                    if (prevNode[e.to] != -1) continue;

                    prevNode[e.to] = u;
                    prevEdge[e.to] = i;

                    if (e.to == t) {
                        reachedT = true;
                        break BFS; // early exit once we reach the sink
                    }
                    q.add(e.to);
                }
            }

            // No augmenting path exists -> current flow is maximum.
            if (!reachedT) break;

            // Compute the bottleneck (minimum residual capacity) along the found path.
            long bottleneck = INF;
            for (int v = t; v != s; v = prevNode[v]) {
                int u = prevNode[v];
                Edge e = g.adj(u).get(prevEdge[v]);
                if (e.cap < bottleneck) bottleneck = e.cap;
            }

            // Augment by the bottleneck and update both forward and reverse residual edges.
            for (int v = t; v != s; v = prevNode[v]) {
                int u = prevNode[v];
                Edge e = g.adj(u).get(prevEdge[v]);  // forward edge u -> v
                Edge rev = g.adj(e.to).get(e.rev);   // reverse edge v -> u

                e.cap -= bottleneck;
                rev.cap += bottleneck;

                e.flow += bottleneck;
                rev.flow -= bottleneck;
            }

            flow += bottleneck;
        }

        return flow;
    }

    /**
     * Computes the maximum flow from {@code s} to {@code t}, optionally emitting visualization frames.
     *
     * <p>If {@code out} is non-null, this method appends:
     * <ul>
     *   <li>a {@link Path} frame for each augmenting path found by BFS,</li>
     *   <li>a {@link Push} frame (one per edge on that path) showing the augmented amount,</li>
     *   <li>and finally a {@link Cut} frame derived from residual reachability from {@code s}.</li>
     * </ul>
     *
     * @param g   residual network (modified in-place)
     * @param s   source node index
     * @param t   sink node index
     * @param out list to append frames to; may be null to disable visualization
     * @return maximum s-t flow value
     */
    public long maxFlow(Graph g, int s, int t, List<VisFrame> out) {
        if (s == t) return 0L;

        final long INF = Long.MAX_VALUE / 4;
        long flow = 0L;
        final int n = g.size();

        while (true) {
            // BFS to find an augmenting path (same logic as the non-visual version).
            int[] prevNode = new int[n];
            int[] prevEdge = new int[n];
            Arrays.fill(prevNode, -1);
            prevNode[s] = s;

            Queue<Integer> q = new ArrayDeque<>();
            q.add(s);

            boolean reachedT = false;
            BFS:
            while (!q.isEmpty()) {
                int u = q.poll();
                for (int i = 0; i < g.adj(u).size(); i++) {
                    Edge e = g.adj(u).get(i);
                    if (e.cap <= 0) continue;
                    if (prevNode[e.to] != -1) continue;

                    prevNode[e.to] = u;
                    prevEdge[e.to] = i;

                    if (e.to == t) {
                        reachedT = true;
                        break BFS;
                    }
                    q.add(e.to);
                }
            }

            if (!reachedT) break;

            // Bottleneck on the found path.
            long bottleneck = INF;
            for (int v = t; v != s; v = prevNode[v]) {
                int u = prevNode[v];
                Edge e = g.adj(u).get(prevEdge[v]);
                if (e.cap < bottleneck) bottleneck = e.cap;
            }

            // (Visualization) Frame 1: the augmenting path as a node sequence.
            if (out != null) {
                out.add(makePathFrame(prevNode, s, t));
            }

            // Augment flow; also collect the path edges for Push visualization.
            List<int[]> pushedEdges = (out != null) ? new ArrayList<>() : null;

            for (int v = t; v != s; v = prevNode[v]) {
                int u = prevNode[v];
                Edge e = g.adj(u).get(prevEdge[v]); // forward edge u -> v
                Edge rev = g.adj(e.to).get(e.rev);  // reverse edge v -> u

                e.cap -= bottleneck;
                rev.cap += bottleneck;

                e.flow += bottleneck;
                rev.flow -= bottleneck;

                if (pushedEdges != null) pushedEdges.add(new int[]{u, v});
            }

            flow += bottleneck;

            // (Visualization) Frame 2: Push events along the path, in s -> t order.
            if (out != null) {
                VisFrame f = new VisFrame();
                // pushedEdges were collected while walking t -> s, so reverse the iteration.
                for (int i = pushedEdges.size() - 1; i >= 0; i--) {
                    int[] uv = pushedEdges.get(i);
                    f.add(new Push(uv[0], uv[1], bottleneck));
                }
                out.add(f);
            }
        }

        // Final (Visualization): minimum cut induced by residual reachability from s.
        if (out != null) {
            boolean[] inS = reachableFromS(g, s);
            VisFrame f = new VisFrame();
            f.add(new Cut(inS));
            out.add(f);
        }

        return flow;
    }

    /**
     * Reconstructs the BFS parent pointers into a node sequence s -> ... -> t and wraps it as a {@link Path} frame.
     *
     * @param prev BFS parent pointers (prev[v] = parent of v)
     * @param s    source node
     * @param t    sink node
     * @return visualization frame containing a {@link Path}
     */
    private VisFrame makePathFrame(int[] prev, int s, int t) {
        // Count number of edges on the path (so we can allocate the array once).
        int len = 0;
        for (int v = t; v != s; v = prev[v]) len++;

        int[] path = new int[len + 1];
        int idx = len;

        // Fill from the end by walking t -> s, then produce s -> t order in the array.
        for (int v = t; ; v = prev[v]) {
            path[idx--] = v;
            if (v == s) break;
        }

        VisFrame f = new VisFrame();
        f.add(new Path(path));
        return f;
    }

    /**
     * Computes residual reachability from {@code s} using only edges with {@code cap > 0}.
     * The reachable set defines the S-side of an s-t minimum cut for the final residual network.
     */
    private boolean[] reachableFromS(Graph g, int s) {
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
