package ega.algorithms;

import ega.core.Edge;
import ega.core.Graph;
import ega.gui.vis.*;   // VisFrame, Path, Push, Cut

import java.util.*;

/**
 * Ford–Fulkerson method for computing a maximum s-t flow.
 *
 * <p>This implementation searches for an augmenting path in the residual network using an
 * iterative DFS (stack-based). After finding any s→t path, it augments by the bottleneck
 * residual capacity on that path and updates the residual network in-place.
 *
 * <p>Notes:
 * <ul>
 *   <li>Path selection is DFS-based, so the running time depends on capacities and path choices
 *       (unlike Edmonds–Karp which uses BFS for a polynomial bound).</li>
 *   <li>The residual network is represented by adjacency lists. Each edge has a reverse edge
 *       index {@code e.rev} pointing into {@code g.adj(e.to)}.</li>
 *   <li>{@code e.cap} is the current residual capacity. {@code e.flow} tracks the current flow.</li>
 * </ul>
 */
public class FordFulkerson {

    /**
     * Computes the maximum flow from {@code s} to {@code t} (no visualization).
     *
     * @param g residual network (modified in-place)
     * @param s source node index
     * @param t sink node index
     * @return maximum s-t flow value
     */
    public long maxFlow(Graph g, int s, int t) {
        if (s == t) return 0L;

        final long INF = Long.MAX_VALUE / 4; // large sentinel value with overflow headroom
        final int n = g.size();

        long flow = 0L;

        // Parent pointers for reconstructing the current augmenting path:
        // prevNode[v] = predecessor node of v, prevEdge[v] = edge index used from prevNode[v] to v.
        int[] prevNode = new int[n];
        int[] prevEdge = new int[n];

        while (true) {
            // Find any augmenting path in the residual network and fill prevNode/prevEdge.
            boolean found = findAugmentingPathDFS(g, s, t, prevNode, prevEdge);
            if (!found) break;

            // Compute the path bottleneck (minimum residual capacity along the path).
            long bottleneck = INF;
            for (int v = t; v != s; v = prevNode[v]) {
                int u = prevNode[v];
                Edge e = g.adj(u).get(prevEdge[v]);
                if (e.cap < bottleneck) bottleneck = e.cap;
            }

            // Augment by bottleneck and update forward/reverse residual edges.
            for (int v = t; v != s; v = prevNode[v]) {
                int u = prevNode[v];
                Edge e = g.adj(u).get(prevEdge[v]); // forward edge u -> v
                Edge rev = g.adj(e.to).get(e.rev);  // reverse edge v -> u

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
     *   <li>a {@link Path} frame for each augmenting path found by DFS,</li>
     *   <li>a {@link Push} frame (one per edge on that path) showing the augmented amount,</li>
     *   <li>and a final {@link Cut} frame derived from residual reachability from {@code s}.</li>
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
        final int n = g.size();
        long flow = 0L;

        int[] prevNode = new int[n];
        int[] prevEdge = new int[n];

        while (true) {
            boolean found = findAugmentingPathDFS(g, s, t, prevNode, prevEdge);
            if (!found) break;

            // Reconstruct the node sequence s -> ... -> t for visualization.
            int[] pathNodes = buildPathNodes(s, t, prevNode);

            // Compute bottleneck on the found augmenting path.
            long bottleneck = INF;
            for (int v = t; v != s; v = prevNode[v]) {
                int u = prevNode[v];
                Edge e = g.adj(u).get(prevEdge[v]);
                if (e.cap < bottleneck) bottleneck = e.cap;
            }

            // Frame 1: show the augmenting path as a node sequence.
            if (out != null) {
                VisFrame pf = new VisFrame();
                pf.add(new Path(pathNodes));
                out.add(pf);
            }

            // Augment and (optionally) collect used edges for Push visualization.
            List<int[]> usedEdges = (out != null) ? new ArrayList<>() : null;

            for (int v = t; v != s; v = prevNode[v]) {
                int u = prevNode[v];
                Edge e = g.adj(u).get(prevEdge[v]);
                Edge rev = g.adj(e.to).get(e.rev);

                e.cap -= bottleneck;
                rev.cap += bottleneck;

                e.flow += bottleneck;
                rev.flow -= bottleneck;

                if (usedEdges != null) usedEdges.add(new int[]{u, v});
            }

            flow += bottleneck;

            // Frame 2: show pushes along the path in s -> t order.
            if (out != null) {
                VisFrame qf = new VisFrame();
                // usedEdges were collected while walking t -> s, so iterate in reverse.
                for (int i = usedEdges.size() - 1; i >= 0; i--) {
                    int[] uv = usedEdges.get(i);
                    qf.add(new Push(uv[0], uv[1], bottleneck));
                }
                out.add(qf);
            }
        }

        // Final frame: reachable set from s in the residual network (cap > 0) defines the S-side of a min cut.
        if (out != null) {
            boolean[] inS = reachableFromS(g, s);
            VisFrame cf = new VisFrame();
            cf.add(new Cut(inS));
            out.add(cf);
        }

        return flow;
    }

    /**
     * Finds any augmenting path from {@code s} to {@code t} in the residual network using an iterative DFS.
     *
     * <p>On success, {@code prevNode} and {@code prevEdge} describe the discovered DFS tree, allowing
     * path reconstruction by following parent pointers from {@code t} back to {@code s}.
     *
     * @param g        residual network
     * @param s        source
     * @param t        sink
     * @param prevNode output: predecessor node on the found DFS tree (filled; -1 means undiscovered)
     * @param prevEdge output: edge index used to enter the node from its predecessor
     * @return {@code true} iff {@code t} is reachable from {@code s} via edges with positive residual capacity
     */
    private boolean findAugmentingPathDFS(Graph g, int s, int t,
                                          int[] prevNode, int[] prevEdge) {
        final int n = g.size();
        Arrays.fill(prevNode, -1);
        Arrays.fill(prevEdge, -1);

        // Per-node "current arc" pointer: next outgoing edge index to try during DFS.
        int[] it = new int[n];

        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(s);
        prevNode[s] = s; // mark source as discovered

        while (!stack.isEmpty()) {
            int u = stack.peek();
            if (u == t) return true; // reached sink; parents describe an s->t path

            List<Edge> adj = g.adj(u);
            boolean advanced = false;

            // Try edges out of u until one advances the DFS.
            while (it[u] < adj.size()) {
                int i = it[u];
                it[u] = i + 1; // advance cursor regardless of whether this edge works
                Edge e = adj.get(i);

                // Only traverse edges with positive residual capacity, and avoid revisiting nodes.
                if (e.cap <= 0) continue;
                int v = e.to;
                if (prevNode[v] != -1) continue;

                // Take edge (u -> v) in the DFS tree.
                prevNode[v] = u;
                prevEdge[v] = i;
                stack.push(v);
                advanced = true;
                break;
            }

            if (!advanced) {
                // Dead end at u: backtrack.
                stack.pop();
            }
        }

        return false;
    }

    /**
     * Reconstructs the node sequence of the current path from {@code s} to {@code t} using {@code prev}.
     *
     * @param s    source
     * @param t    sink
     * @param prev predecessor array (prev[v] = predecessor of v)
     * @return nodes on the path in order s -> ... -> t
     */
    private int[] buildPathNodes(int s, int t, int[] prev) {
        // Count number of edges on the path to allocate exactly once.
        int len = 0;
        for (int v = t; v != s; v = prev[v]) len++;

        int[] nodes = new int[len + 1];
        int idx = len;

        // Fill backwards by walking t -> s, producing nodes in s -> t order in the array.
        for (int v = t; ; v = prev[v]) {
            nodes[idx--] = v;
            if (v == s) break;
        }

        return nodes;
    }

    /**
     * Computes residual reachability from {@code s} using only edges with {@code cap > 0}.
     * The reachable set defines the S-side of an s-t minimum cut after the algorithm terminates.
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
