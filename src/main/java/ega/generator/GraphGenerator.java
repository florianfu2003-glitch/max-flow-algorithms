package ega.generator;

import ega.algorithms.Dinic;
import ega.core.Edge;
import ega.core.Graph;

import java.awt.geom.Line2D;
import java.util.*;

/**
 * Random max-flow instance generator (geometric / planar-style).
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Sample points in the unit square [0,1]^2 with a minimum separation.</li>
 *   <li>Sort all point pairs by distance and greedily add non-crossing undirected edges
 *       (also forbids "passing through" an intermediate vertex and collinear overlap).</li>
 *   <li>If multiple connected components exist, connect them by adding the shortest possible
 *       non-crossing "bridge" edges to make the graph connected.</li>
 *   <li>After connectivity, try to add all remaining non-crossing edges (approx. maximal planar).</li>
 *   <li>Convert each undirected edge {u,v} into two original directed edges u->v and v->u with
 *       positive capacities (reverse residual edges are created by {@link Graph#addEdge}).</li>
 *   <li>Select a non-degenerate (s,t) pair using a relaxed but effective criterion that avoids
 *       trivial cuts (pure s-cut / pure t-cut) and excludes cases where max-flow equals
 *       capOut(s) or capIn(t).</li>
 * </ol>
 */
public class GraphGenerator {

    public static class Result {
        public final Graph graph;
        public final int s, t;
        public final double[] x, y;

        /**
         * Normalized minimum separation between points in [0,1]^2.
         * With typical GUI node radius (~10px) and canvas height (~600–700px),
         * a value around 0.07 helps avoid node overlap.
         */
        private static final double MIN_SEP_NORM = 0.07;

        public Result(Graph graph, int s, int t, double[] x, double[] y) {
            this.graph = graph;
            this.s = s;
            this.t = t;
            this.x = x;
            this.y = y;
        }
    }

    /**
     * Generates a random instance with {@code n} vertices and capacities in [1, maxCap].
     *
     * @param n      number of vertices (>= 3)
     * @param maxCap maximum capacity bound (> 0)
     * @param rng    random generator
     * @return generated residual network and the chosen (s,t) along with point coordinates
     */
    public static Result generate(int n, int maxCap, Random rng) {
        if (n < 3) throw new IllegalArgumentException("At least three points are required.");
        if (maxCap <= 0) throw new IllegalArgumentException("maxCap must be > 0.");

        // 1) Sample points with a minimum separation.
        double[] xs = new double[n], ys = new double[n];
        samplePointsWithMinSep(xs, ys, rng, Result.MIN_SEP_NORM);

        // 2) Build candidate undirected edges and sort by squared distance.
        List<UndirEdge> cand = new ArrayList<>();
        for (int u = 0; u < n; u++) {
            for (int v = u + 1; v < n; v++) {
                double dx = xs[u] - xs[v], dy = ys[u] - ys[v];
                cand.add(new UndirEdge(u, v, dx * dx + dy * dy));
            }
        }
        cand.sort(Comparator.comparingDouble(e -> e.dist2));

        // 3) Greedily add non-crossing edges to obtain a planar-ish edge set.
        List<UndirEdge> planar = new ArrayList<>();
        for (UndirEdge e : cand) {
            if (!wouldCross(planar, e, xs, ys)) planar.add(e);
        }

        // 4) If disconnected, connect components by adding shortest non-crossing bridges.
        bridgeComponentsPlanar(planar, xs, ys);

        // 5) After connected, try to add all remaining non-crossing edges (approx. maximal planar).
        saturatePlanarEdges(planar, cand, xs, ys);

        // 6) Convert to a directed graph:
        //    For each undirected edge {u,v}, add u->v and v->u as original directed edges with positive capacity.
        Graph g = new Graph(n);
        for (UndirEdge e : planar) {
            int u = e.u, v = e.v;
            long capUV = 1L + rng.nextInt(maxCap);
            long capVU = 1L + rng.nextInt(maxCap);
            g.addEdge(u, v, capUV); // also creates reverse residual edge (origCap=0)
            g.addEdge(v, u, capVU);
        }

        // 7) Select (s,t) with a relaxed non-degeneracy criterion and trivial-cut filtering.
        int s = -1, t = -1;
        outer:
        for (int candS = 0; candS < n; candS++) {
            for (int candT = 0; candT < n; candT++) {
                if (candS == candT) continue;
                if (isGoodPair(g, candS, candT)) {
                    s = candS;
                    t = candT;
                    break outer;
                }
            }
        }

        // Fallback (rare): if strict checks fail, choose left-most as source and right-most as sink.
        if (s == -1) {
            s = pickSource(xs);
            t = pickSink(xs);
            if (s == t) t = (s + 1) % n;
        }

        return new Result(g, s, t, xs, ys);
    }

    /** Lightweight structure for an undirected edge candidate (endpoints and squared distance). */
    private static class UndirEdge {
        final int u, v;
        final double dist2;

        UndirEdge(int u, int v, double d2) {
            this.u = u;
            this.v = v;
            this.dist2 = d2;
        }
    }

    /**
     * Checks whether adding {@code cand} to {@code chosen} would violate geometric constraints:
     * <ul>
     *   <li>the segment passes strictly through a third vertex,</li>
     *   <li>the segment properly intersects an existing segment (excluding shared endpoints),</li>
     *   <li>or the segment is collinear with an existing segment and overlaps with positive length.</li>
     * </ul>
     */
    private static boolean wouldCross(List<UndirEdge> chosen, UndirEdge cand, double[] xs, double[] ys) {
        final double x1 = xs[cand.u], y1 = ys[cand.u];
        final double x2 = xs[cand.v], y2 = ys[cand.v];

        // Disallow "passing through" any other vertex (strictly inside the segment).
        for (int w = 0; w < xs.length; w++) {
            if (w == cand.u || w == cand.v) continue;
            if (pointStrictlyOnSegment(xs[w], ys[w], x1, y1, x2, y2)) return true;
        }

        // Disallow intersections/overlaps with any already chosen segment.
        Line2D segCand = new Line2D.Double(x1, y1, x2, y2);
        for (UndirEdge e : chosen) {
            double ax = xs[e.u], ay = ys[e.u], bx = xs[e.v], by = ys[e.v];

            // Collinear overlap with positive length (touching at endpoints only is allowed).
            if (segmentsCollinearOverlap(x1, y1, x2, y2, ax, ay, bx, by)) return true;

            // Standard intersection test, excluding edges that share an endpoint.
            boolean shareEnd = (e.u == cand.u) || (e.u == cand.v) || (e.v == cand.u) || (e.v == cand.v);
            Line2D segExist = new Line2D.Double(ax, ay, bx, by);
            if (!shareEnd && segCand.intersectsLine(segExist)) return true;
        }

        return false;
    }

    /**
     * Ensures connectivity by repeatedly adding the shortest possible non-crossing "bridge"
     * from component 0 to each other component.
     *
     * <p>If bridging becomes impossible under the "no crossing" constraint (rare), the method stops
     * to avoid an infinite loop.
     */
    private static void bridgeComponentsPlanar(List<UndirEdge> edges, double[] xs, double[] ys) {
        int n = xs.length;

        while (true) {
            int[] comp = calcComponents(n, edges);
            int comps = 0;
            for (int v = 0; v < n; v++) comps = Math.max(comps, comp[v] + 1);
            if (comps <= 1) return;

            boolean merged = false;
            for (int c = 1; c < comps; c++) {
                // Try all bridges between component 0 and component c, in increasing distance order.
                List<UndirEdge> bridges = new ArrayList<>();
                for (int u = 0; u < n; u++) if (comp[u] == 0) {
                    for (int v = 0; v < n; v++) if (comp[v] == c) {
                        double dx = xs[u] - xs[v], dy = ys[u] - ys[v];
                        bridges.add(new UndirEdge(u, v, dx * dx + dy * dy));
                    }
                }
                bridges.sort(Comparator.comparingDouble(b -> b.dist2));

                UndirEdge chosen = null;
                for (UndirEdge b : bridges) {
                    if (!wouldCross(edges, b, xs, ys)) {
                        chosen = b;
                        break;
                    }
                }

                if (chosen != null) {
                    edges.add(chosen);
                    merged = true;
                    break;
                }
            }

            if (!merged) return; // no feasible bridge found under constraints
        }
    }

    /** Computes undirected connected components induced by {@code edges}, returning component ids 0..k-1. */
    private static int[] calcComponents(int n, List<UndirEdge> edges) {
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());

        for (UndirEdge e : edges) {
            adj.get(e.u).add(e.v);
            adj.get(e.v).add(e.u);
        }

        int[] comp = new int[n];
        Arrays.fill(comp, -1);

        int cid = 0;
        for (int i = 0; i < n; i++) {
            if (comp[i] != -1) continue;

            Deque<Integer> st = new ArrayDeque<>();
            st.push(i);
            comp[i] = cid;

            while (!st.isEmpty()) {
                int u = st.pop();
                for (int w : adj.get(u)) {
                    if (comp[w] == -1) {
                        comp[w] = cid;
                        st.push(w);
                    }
                }
            }
            cid++;
        }

        return comp;
    }

    /**
     * After connectivity, keep adding any remaining candidate edges that do not violate the
     * "no crossing / no passing-through-vertex / no collinear overlap" constraints.
     *
     * <p>This performs repeated scans until no further edge can be added.
     */
    private static void saturatePlanarEdges(List<UndirEdge> edges,
                                            List<UndirEdge> candidates,
                                            double[] xs,
                                            double[] ys) {
        boolean improved = true;
        while (improved) {
            improved = false;
            for (UndirEdge c : candidates) {
                if (containsUndirEdge(edges, c.u, c.v)) continue;
                if (!wouldCross(edges, c, xs, ys)) {
                    edges.add(c);
                    improved = true;
                }
            }
        }
    }

    /** Returns true iff {@code edges} already contains the undirected edge {a,b}. */
    private static boolean containsUndirEdge(List<UndirEdge> edges, int a, int b) {
        int u = Math.min(a, b), v = Math.max(a, b);
        for (UndirEdge e : edges) {
            int uu = Math.min(e.u, e.v), vv = Math.max(e.u, e.v);
            if (uu == u && vv == v) return true;
        }
        return false;
    }

    /** Picks the left-most point (minimum x) as a fallback source. */
    private static int pickSource(double[] xs) {
        int s = 0;
        for (int i = 1; i < xs.length; i++) if (xs[i] < xs[s]) s = i;
        return s;
    }

    /** Picks the right-most point (maximum x) as a fallback sink. */
    private static int pickSink(double[] xs) {
        int t = 0;
        for (int i = 1; i < xs.length; i++) if (xs[i] > xs[t]) t = i;
        return t;
    }

    /**
     * Total residual outgoing capacity of s, counting only original edges (origCap>0),
     * and ignoring pure residual edges (origCap==0).
     */
    private static long totalOutCapacity(Graph g, int s) {
        long sum = 0;
        for (Edge e : g.adj(s)) {
            if (e.cap > 0 && e.origCap > 0) sum += e.cap;
        }
        return sum;
    }

    /**
     * Total residual incoming capacity into t, counting only original edges (origCap>0).
     */
    private static long totalInCapacity(Graph g, int t) {
        long sum = 0;
        int n = g.size();
        for (int u = 0; u < n; u++) {
            for (Edge e : g.adj(u)) {
                if (e.cap > 0 && e.origCap > 0 && e.to == t) sum += e.cap;
            }
        }
        return sum;
    }

    /**
     * Relaxed but effective (s,t) quality criterion:
     *
     * <p>We compute max-flow f on a cloned graph (so the original stays intact). On the final residual network,
     * compute S = vertices reachable from s using edges with residual capacity cap>0.
     *
     * <p>We reject:
     * <ul>
     *   <li>Trivial cuts: f == capOut(s) or f == capIn(t)</li>
     *   <li>"Pure s-cut": all original out-neighbors of s lie outside S (i.e., only s is in S near the source)</li>
     *   <li>"Pure t-cut": all original in-neighbors of t come from S (i.e., the cut isolates only t)</li>
     * </ul>
     */
    private static boolean isGoodPair(Graph g, int s, int t) {
        if (s == t) return false;

        long capOutS = totalOutCapacity(g, s);
        long capInT = totalInCapacity(g, t);

        // Compute max-flow on a clone (Dinic modifies the residual network).
        Graph cloned = g.cloneGraph();
        long f = new Dinic().maxFlow(cloned, s, t);

        // Filter out trivial cuts.
        if (f == capOutS) return false;
        if (f == capInT) return false;

        // Compute S in the final residual network.
        boolean[] inS = residualReachable(cloned, s);

        // Not a "pure s-cut": at least one original out-neighbor of s stays in S.
        boolean sHasNeighborInS = false;
        for (Edge e : g.adj(s)) {
            if (e.origCap > 0 && inS[e.to]) {
                sHasNeighborInS = true;
                break;
            }
        }
        if (!sHasNeighborInS) return false;

        // Not a "pure t-cut": at least one original in-neighbor u->t has u on the T-side (u not in S).
        boolean tHasInNeighborInT = false;
        for (int u = 0; u < g.size(); u++) {
            for (Edge e : g.adj(u)) {
                if (e.origCap > 0 && e.to == t && !inS[u]) {
                    tHasInNeighborInT = true;
                    break;
                }
            }
            if (tHasInNeighborInT) break;
        }
        if (!tHasInNeighborInT) return false;

        return true;
    }

    /**
     * Residual reachability by BFS using only edges with cap>0.
     *
     * <p>Must be called on the <b>final residual network</b> (after max-flow computation) if used
     * for cut-based reasoning.
     */
    private static boolean[] residualReachable(Graph g, int s) {
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

    /**
     * Samples points in [0,1]^2 while trying to maintain a minimum distance between any two points.
     * If placement becomes too hard, the minimum distance is gradually relaxed.
     */
    private static void samplePointsWithMinSep(double[] xs, double[] ys, Random rng, double minSep) {
        int n = xs.length;
        double cur = minSep, cur2 = cur * cur;

        for (int i = 0; i < n; i++) {
            int tries = 0;
            while (true) {
                double x = rng.nextDouble(), y = rng.nextDouble();

                boolean ok = true;
                for (int j = 0; j < i; j++) {
                    double dx = x - xs[j], dy = y - ys[j];
                    if (dx * dx + dy * dy < cur2) {
                        ok = false;
                        break;
                    }
                }

                if (ok) {
                    xs[i] = x;
                    ys[i] = y;
                    break;
                }

                // If we tried too often, relax the constraint slightly (but keep a floor).
                if (++tries > 2000 && cur > 1e-3) {
                    cur *= 0.95;
                    cur2 = cur * cur;
                }
            }
        }
    }

    /**
     * Returns true iff segments (x1,y1)-(x2,y2) and (x3,y3)-(x4,y4) are collinear and overlap with
     * positive length (touching only at endpoints does not count as overlap).
     */
    private static boolean segmentsCollinearOverlap(double x1, double y1, double x2, double y2,
                                                    double x3, double y3, double x4, double y4) {
        final double EPS = 1e-6;

        double ux = x2 - x1, uy = y2 - y1;

        // Check collinearity of points 3 and 4 with the line through 1->2 via cross products.
        double c1 = (x3 - x1) * uy - (y3 - y1) * ux;
        double c2 = (x4 - x1) * uy - (y4 - y1) * ux;
        if (Math.abs(c1) > EPS || Math.abs(c2) > EPS) return false;

        // Project onto the dominant axis to test overlap length.
        if (Math.abs(ux) >= Math.abs(uy)) {
            double a1 = Math.min(x1, x2), a2 = Math.max(x1, x2);
            double b1 = Math.min(x3, x4), b2 = Math.max(x3, x4);
            return (Math.min(a2, b2) - Math.max(a1, b1)) > EPS;
        } else {
            double a1 = Math.min(y1, y2), a2 = Math.max(y1, y2);
            double b1 = Math.min(y3, y4), b2 = Math.max(y3, y4);
            return (Math.min(a2, b2) - Math.max(a1, b1)) > EPS;
        }
    }

    /**
     * Returns true iff point P lies strictly inside segment AB (excluding endpoints),
     * within a small numerical tolerance.
     */
    private static boolean pointStrictlyOnSegment(double px, double py,
                                                  double ax, double ay,
                                                  double bx, double by) {
        final double EPS = 1e-6;

        double abx = bx - ax, aby = by - ay;
        double apx = px - ax, apy = py - ay;

        // Collinearity test via cross product (AB x AP).
        double cross = abx * apy - aby * apx;
        if (Math.abs(cross) > EPS) return false;

        // Strictly between endpoints test via projection parameter t in (0,1).
        double dot = apx * abx + apy * aby;
        double len2 = abx * abx + aby * aby;
        if (len2 < EPS) return false;

        double t = dot / len2;
        return t > EPS && t < 1.0 - EPS;
    }
}
