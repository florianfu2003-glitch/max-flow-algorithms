package ega.core;

/**
 * Directed edge in a residual network for max-flow algorithms.
 *
 * <p>This class is designed for adjacency-list residual graphs where every forward edge
 * has a corresponding reverse edge. Residual capacity updates are done in-place.</p>
 *
 * <p>Typical invariants used by the algorithms in this project:
 * <ul>
 *   <li>{@code cap} is the current residual capacity on this directed residual edge.</li>
 *   <li>{@code flow} stores the flow value on the corresponding original edge direction
 *       (reverse edges typically carry negative flow).</li>
 *   <li>{@code rev} is the index of the reverse edge in {@code adj[to]} (i.e., at the head vertex).</li>
 *   <li>{@code origCap} stores the initial capacity of the corresponding forward edge
 *       (for reverse edges this is usually 0).</li>
 * </ul>
 * </p>
 */
public class Edge {

    /** Head vertex of this directed edge (u -> to). */
    public int to;

    /**
     * Index of the reverse edge in the adjacency list of {@code to}.
     * If this edge is stored in {@code adj[u]}, then the reverse edge is {@code adj[to].get(rev)}.
     */
    public int rev;

    /**
     * Current residual capacity on this directed edge.
     * Algorithms decrease this when pushing flow, and increase {@code cap} on the reverse edge.
     */
    public long cap;

    /**
     * Current flow value associated with this edge direction.
     * For a forward edge, {@code flow} increases when we push flow.
     * For the reverse edge, {@code flow} typically decreases (becomes negative).
     */
    public long flow;

    /**
     * Original capacity of the corresponding forward edge at construction time.
     * For reverse edges this is typically 0. Useful for reporting/visualization or resets.
     */
    public long origCap;

    /**
     * Creates an edge record for residual-graph algorithms.
     *
     * @param to      head vertex
     * @param rev     index of the reverse edge in {@code adj[to]}
     * @param cap     initial residual capacity for this edge
     * @param origCap original capacity of the corresponding forward edge (reverse edge: 0)
     */
    public Edge(int to, int rev, long cap, long origCap) {
        this.to = to;
        this.rev = rev;
        this.cap = cap;
        this.origCap = origCap;
        this.flow = 0L;
    }
}
