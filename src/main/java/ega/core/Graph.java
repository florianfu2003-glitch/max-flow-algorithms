package ega.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Directed graph (residual network representation) shared by all max-flow algorithms in this project.
 *
 * <p>Representation:
 * <ul>
 *   <li>Adjacency-list storage: {@code adj[u]} is the list of outgoing residual edges from node {@code u}.</li>
 *   <li>For every original directed edge (u -> v) with capacity C, we store:
 *     <ul>
 *       <li>a forward residual edge with {@code cap = C} and {@code origCap = C}</li>
 *       <li>a reverse residual edge with {@code cap = 0} and {@code origCap = 0}</li>
 *     </ul>
 *   </li>
 *   <li>Each edge stores {@code rev}, the index of its reverse edge in the adjacency list of its head vertex.</li>
 * </ul>
 *
 * <p>Residual updates are performed in-place by the algorithms by decreasing {@code cap} on the used forward edge
 * and increasing {@code cap} on its reverse edge.
 */
public class Graph {

    /** Number of vertices. Vertices are indexed 0..n-1. */
    private final int n;

    /** Adjacency lists of residual edges. */
    private final List<List<Edge>> adj;

    /**
     * Creates an empty residual network with {@code n} vertices and no edges.
     *
     * @param n number of vertices
     */
    public Graph(int n) {
        this.n = n;
        this.adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>());
        }
    }

    /**
     * @return number of vertices in the graph
     */
    public int size() {
        return n;
    }

    /**
     * Returns the outgoing residual adjacency list of {@code u}.
     *
     * <p>Algorithms iterate over this list to traverse and update residual edges.</p>
     *
     * @param u vertex index
     * @return list of outgoing residual edges from {@code u}
     */
    public List<Edge> adj(int u) {
        return adj.get(u);
    }

    /**
     * Adds an original directed edge (u -> v) with capacity {@code cap} to the residual network.
     *
     * <p>This method creates a standard forward/reverse residual edge pair:
     * <ul>
     *   <li>Forward edge: {@code u -> v}, residual capacity {@code cap}, {@code origCap = cap}</li>
     *   <li>Reverse edge: {@code v -> u}, residual capacity {@code 0}, {@code origCap = 0}</li>
     * </ul>
     *
     * <p>The reverse indices ({@code rev}) are set so that:
     * <ul>
     *   <li>if {@code fwd} is stored in {@code adj[u]} at index i, then {@code adj[v].get(fwd.rev)} is the reverse edge</li>
     *   <li>if {@code rev} is stored in {@code adj[v]} at index j, then {@code adj[u].get(rev.rev)} is the forward edge</li>
     * </ul>
     *
     * @param u   tail vertex
     * @param v   head vertex
     * @param cap capacity of the original edge
     */
    public void addEdge(int u, int v, long cap) {
        // Forward edge points to v and its reverse will be the next edge appended to adj[v].
        Edge fwd = new Edge(v, adj.get(v).size(), cap, cap);

        // Reverse edge points back to u and its reverse is the next edge appended to adj[u] (i.e., fwd).
        Edge rev = new Edge(u, adj.get(u).size(), 0L, 0L);

        adj.get(u).add(fwd);
        adj.get(v).add(rev);
    }

    /**
     * Private constructor used by {@link #cloneGraph()} to wrap a pre-built adjacency structure.
     */
    private Graph(int n, List<List<Edge>> adj) {
        this.n = n;
        this.adj = adj;
    }

    /**
     * Deep-copies this graph including the complete residual state.
     *
     * <p>This method copies:
     * <ul>
     *   <li>the adjacency-list structure (new lists)</li>
     *   <li>all edges, including {@code to}, {@code rev}, {@code cap}, {@code flow}, and {@code origCap}</li>
     * </ul>
     *
     * <p>Important:
     * We intentionally do <b>not</b> call {@link #addEdge(int, int, long)} here, because that would rebuild
     * edges from scratch and lose the current residual/flow state. The goal is an exact snapshot clone.</p>
     *
     * @return a deep copy of this residual network
     */
    public Graph cloneGraph() {
        // 1) Create an empty outer adjacency list with the same size.
        List<List<Edge>> adjCopy = new ArrayList<>(n);
        for (int u = 0; u < n; u++) {
            adjCopy.add(new ArrayList<>(adj.get(u).size()));
        }

        // 2) Copy each edge record verbatim (including current residual capacity and flow).
        for (int u = 0; u < n; u++) {
            List<Edge> rowOrig = adj.get(u);
            List<Edge> rowCopy = adjCopy.get(u);

            for (Edge e : rowOrig) {
                Edge ne = new Edge(e.to, e.rev, e.cap, e.origCap);
                ne.flow = e.flow;
                rowCopy.add(ne);
            }
        }

        // 3) Wrap with a Graph object using the private constructor.
        return new Graph(n, adjCopy);
    }
}
