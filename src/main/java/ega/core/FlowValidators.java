package ega.core;

import java.util.ArrayDeque;

/**
 * Utility validators for max-flow solutions in the project's residual-graph representation.
 *
 * <p>These checks are typically used in a test environment to verify correctness properties:
 * <ol>
 *   <li><b>Capacity constraints</b> on original (real) edges</li>
 *   <li><b>Flow conservation</b> at all intermediate vertices</li>
 *   <li><b>Existence of a saturated s-t cut</b> in the final residual network</li>
 * </ol>
 *
 * <p><b>Important representation detail:</b>
 * The project uses {@code origCap} to distinguish <i>original forward edges</i> from <i>pure residual reverse edges</i>.
 * <ul>
 *   <li>Original forward edge: {@code origCap > 0} (represents a real capacity constraint).</li>
 *   <li>Pure residual reverse edge: {@code origCap == 0} (implementation artifact for residual updates).</li>
 * </ul>
 *
 * <p>All validators below intentionally inspect only edges with {@code origCap > 0} where appropriate,
 * to avoid double counting and to avoid treating residual-only edges as "real" pipes.
 */
public class FlowValidators {

    /**
     * Capacity constraints on original edges:
     * for every edge with {@code origCap > 0}, verify {@code 0 <= flow <= origCap}.
     *
     * <p>Pure residual edges ({@code origCap == 0}) are ignored because they do not represent real capacity
     * constraints from the input instance.
     */
    public static boolean capacityConstraints(Graph g) {
        final int n = g.size();
        for (int u = 0; u < n; u++) {
            for (Edge e : g.adj(u)) {
                if (e.origCap <= 0) continue;   // only original forward edges
                long f = e.flow;
                long C = e.origCap;

                // Flow on an original forward edge must never be negative and must not exceed its original capacity.
                if (f < 0L) return false;
                if (f > C) return false;
            }
        }
        return true;
    }

    /**
     * Flow conservation on original edges:
     *
     * <p>For every original forward edge {@code u -> v} with flow {@code f}:
     * <ul>
     *   <li>{@code net[u] -= f} (u sends out f)</li>
     *   <li>{@code net[v] += f} (v receives f)</li>
     * </ul>
     *
     * <p>After accumulating over all original edges, require {@code net[u] == 0} for all {@code u != s,t}.
     */
    public static boolean flowConservation(Graph g, int s, int t) {
        final int n = g.size();
        long[] net = new long[n];

        for (int u = 0; u < n; u++) {
            for (Edge e : g.adj(u)) {
                if (e.origCap > 0) {
                    long f = e.flow;
                    int v = e.to;
                    net[u] -= f;
                    net[v] += f;
                }
            }
        }

        for (int u = 0; u < n; u++) {
            if (u == s || u == t) continue;
            if (net[u] != 0L) return false;
        }
        return true;
    }

    /**
     * Saturated cut existence check (a standard max-flow optimality certificate):
     *
     * <p>Compute the set S of vertices reachable from {@code s} in the residual network
     * using only residual edges with {@code cap > 0}. Then require:
     * <ol>
     *   <li>{@code t} is not reachable (i.e., {@code t ∉ S})</li>
     *   <li>Every original forward edge crossing from S to V\\S is saturated:
     *       for any original edge {@code u -> v} with {@code u ∈ S} and {@code v ∉ S},
     *       its residual capacity must be {@code cap(u,v) == 0}.</li>
     * </ol>
     *
     * <p>We only test crossings for original edges ({@code origCap > 0}) and ignore pure residual edges
     * to avoid false positives.
     */
    public static boolean saturatedCutExists(Graph g, int s, int t) {
        final boolean[] inS = residualReachable(g, s);

        // In a max-flow, the sink must be unreachable from s in the final residual network.
        if (inS[t]) return false;

        // Every original edge from S to T must have zero residual capacity (i.e., be saturated).
        final int n = g.size();
        for (int u = 0; u < n; u++) {
            if (!inS[u]) continue; // only consider vertices on the S-side
            for (Edge e : g.adj(u)) {
                if (e.origCap <= 0) continue; // only original forward edges
                if (!inS[e.to] && e.cap > 0) {
                    // Still has residual capacity across the cut -> not saturated -> not a max-flow certificate.
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Residual reachability from {@code s} using only edges with {@code cap > 0}.
     *
     * @return boolean array {@code vis} where {@code vis[v] == true} iff v is reachable from s
     *         in the current residual network.
     */
    private static boolean[] residualReachable(Graph g, int s) {
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
