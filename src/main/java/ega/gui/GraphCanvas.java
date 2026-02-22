package ega.gui;

import ega.core.Edge;
import ega.core.Graph;
import ega.gui.vis.*;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Swing canvas for drawing:
 * <ul>
 *   <li>the current residual network (base graph), and</li>
 *   <li>a single visualization frame consisting of events such as
 *       {@link Path}, {@link Push}, {@link Levels}, {@link Relabel}, {@link Cut}.</li>
 * </ul>
 *
 * <p>Rendering model:
 * <ol>
 *   <li>Draw a gray "base" undirected skeleton for all original directed edges (origCap &gt; 0).</li>
 *   <li>Overlay the current frame events (path edges, push edges + labels, cut edges, etc.).</li>
 *   <li>Draw nodes (optionally colored by levels), then optional node highlights (path/relabel).</li>
 * </ol>
 *
 * <p>Shadow state:
 * This class maintains a per-original-directed-edge shadow table ({@code edgeState}) that tracks
 * the "used" flow amount on that original edge. Before rendering a frame, we apply all {@link Push}
 * events in that frame to the shadow table so that the labels shown on edges stay synchronized with
 * the animation even if the underlying {@link Graph} is not mutated per frame.
 */
public class GraphCanvas extends JPanel {

    /* ====================== Visual constants ====================== */

    private static final int PREF_W = 600;
    private static final int PREF_H = 400;
    private static final double PAD = 40.0;
    private static final int NODE_R = 10;

    /** Base graph stroke (drawn as undirected skeleton). */
    private static final Stroke STROKE_BASE =
            new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Color COLOR_BASE = new Color(120, 120, 120);

    /** Event strokes. */
    private static final Stroke STROKE_THICK =
            new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke STROKE_DASH =
            new BasicStroke(2.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                    10f, new float[]{8f, 6f}, 0f);

    /** Palette. */
    private static final Color COLOR_PATH = new Color(66, 133, 244);
    private static final Color COLOR_PUSH = new Color(255, 193, 7);
    private static final Color COLOR_CUT  = new Color(156, 39, 176);
    private static final Color COLOR_S    = new Color(50, 200, 80);
    private static final Color COLOR_T    = new Color(240, 140, 140);
    private static final Color COLOR_NODE = new Color(230, 230, 230);
    private static final Color COLOR_OUTLINE = Color.DARK_GRAY;
    private static final Color COLOR_PATH_NODE = new Color(66, 133, 244);
    private static final Color COLOR_RELABEL = new Color(255, 111, 0);

    /** Push delta bubble style. */
    private static final Color BUBBLE_FILL = new Color(255, 244, 157, 230);
    private static final Color BUBBLE_EDGE = new Color(180, 140, 0, 220);
    private static final Color BUBBLE_TEXT = new Color(90, 70, 0);

    /* ====================== Canvas state ====================== */

    private Graph graph;
    private double[] xs, ys;
    private int s = -1, t = -1;
    private VisFrame currentFrame;

    /**
     * Shadow table for original directed edges only.
     * Key = {@code dirKey(u,v)}.
     */
    private static final class EdgeInfo {
        long origCap; // original capacity (C)
        long used;    // current used flow on the original directed edge (0..C)
    }
    private final Map<Long, EdgeInfo> edgeState = new HashMap<>();

    public GraphCanvas() {
        setPreferredSize(new Dimension(PREF_W, PREF_H));
        setBackground(Color.white);
        setOpaque(true);
    }

    /**
     * Sets the graph and its node coordinates, and rebuilds the shadow edge state from {@link Graph}.
     */
    public void setGraphData(Graph g, double[] xs, double[] ys, int s, int t) {
        this.graph = g;
        this.xs = xs;
        this.ys = ys;
        this.s = s;
        this.t = t;
        rebuildShadowFromGraph();
        repaint();
    }

    /**
     * Sets the current visualization frame. Before painting, we apply all {@link Push} events
     * in the frame to the shadow table so that edge labels reflect the same step the viewer sees.
     */
    public void setFrame(VisFrame f) {
        applyPushesToShadow(f);
        this.currentFrame = f;
        repaint();
    }

    /**
     * Clears the current frame and resets the shadow table to the current graph's stored flow state
     * (typically all-zero if the algorithms are not mutating the graph between frames).
     */
    public void clearFrame() {
        this.currentFrame = null;
        rebuildShadowFromGraph();
        repaint();
    }

    /* ====================== Shadow state (build / incremental update) ====================== */

    /**
     * Rebuilds {@link #edgeState} by scanning all original directed edges in the graph.
     * Only edges with {@code origCap > 0} are considered "original" and are inserted.
     */
    private void rebuildShadowFromGraph() {
        edgeState.clear();
        if (graph == null) return;

        for (int u = 0; u < graph.size(); u++) {
            for (Edge e : graph.adj(u)) {
                if (e.origCap > 0) {
                    long key = dirKey(u, e.to);
                    EdgeInfo info = new EdgeInfo();
                    info.origCap = Math.max(0, e.origCap);

                    // If the graph already contains a non-zero flow, display it consistently.
                    long used = e.flow;
                    if (used < 0) used = 0;
                    if (used > info.origCap) used = info.origCap;
                    info.used = used;

                    edgeState.put(key, info);
                }
            }
        }
    }

    /**
     * Applies all {@link Push} events of the frame to the shadow table:
     * <ul>
     *   <li>If the push matches an original edge u->v, increase used by +Δ.</li>
     *   <li>Else if it matches the reverse direction of some original edge v->u, decrease used by −Δ.</li>
     * </ul>
     * The value is always clamped to {@code [0, origCap]}.
     */
    private void applyPushesToShadow(VisFrame f) {
        if (f == null || f.events == null) return;

        for (VisEvent ev : f.events) {
            if (!(ev instanceof Push)) continue;

            Push p = (Push) ev;
            int u = p.u, v = p.v;
            long delta = Math.max(0, p.delta);

            long kF = dirKey(u, v);
            long kR = dirKey(v, u);

            if (edgeState.containsKey(kF)) {
                EdgeInfo info = edgeState.get(kF);
                info.used = clamp(info.used + delta, 0, info.origCap);
            } else if (edgeState.containsKey(kR)) {
                EdgeInfo info = edgeState.get(kR);
                info.used = clamp(info.used - delta, 0, info.origCap);
            }
        }
    }

    private static long clamp(long x, long lo, long hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    /* ====================== Painting ====================== */

    @Override
    protected void paintComponent(Graphics gRaw) {
        super.paintComponent(gRaw);

        Graphics2D g2 = (Graphics2D) gRaw.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (graph == null || xs == null || ys == null) {
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawString("GraphCanvas (graph will be rendered here)", 20, 20);
            g2.dispose();
            return;
        }

        final int n = graph.size();
        final int W = getWidth(), H = getHeight();

        // Map normalized coordinates [0,1]x[0,1] to screen coordinates with padding.
        int[] scrX = new int[n];
        int[] scrY = new int[n];
        for (int i = 0; i < n; i++) {
            scrX[i] = (int) Math.round(PAD + xs[i] * (W - 2 * PAD));
            scrY[i] = (int) Math.round(PAD + ys[i] * (H - 2 * PAD));
        }

        // (1) Base graph: draw each undirected edge once, but only for original directed edges (origCap > 0).
        g2.setStroke(STROKE_BASE);
        g2.setColor(COLOR_BASE);
        Set<Long> drawn = new HashSet<>(Math.max(16, n * 4));
        for (int u = 0; u < n; u++) {
            for (Edge e : graph.adj(u)) {
                if (e.origCap <= 0) continue;
                int v = e.to;
                long key = undirectedKey(u, v);
                if (drawn.add(key)) {
                    g2.drawLine(scrX[u], scrY[u], scrX[v], scrY[v]);
                }
            }
        }

        // Extract Levels event if present (used to color nodes by level/height).
        int[] levels = null;
        int Lmax = -1;
        if (currentFrame != null && currentFrame.events != null) {
            for (VisEvent ev : currentFrame.events) {
                if (ev instanceof Levels) {
                    levels = ((Levels) ev).level;
                    if (levels != null) {
                        for (int v = 0; v < Math.min(levels.length, n); v++) {
                            if (levels[v] > Lmax) Lmax = levels[v];
                        }
                    }
                    break;
                }
            }
        }

        // (2) Event overlays: Path / Push / Cut.
        boolean[] pathNodeMark = null;
        if (currentFrame != null && currentFrame.events != null) {
            Stroke saved = g2.getStroke();

            // Path: draw the whole augmenting path in a thick stroke.
            for (VisEvent ev : currentFrame.events) {
                if (!(ev instanceof Path)) continue;
                Path p = (Path) ev;
                int[] a = p.nodes;
                if (a == null || a.length < 2) continue;

                g2.setColor(COLOR_PATH);
                g2.setStroke(STROKE_THICK);
                for (int i = 0; i + 1 < a.length; i++) {
                    int uu = a[i], vv = a[i + 1];
                    if (uu < 0 || vv < 0 || uu >= n || vv >= n) continue;
                    g2.drawLine(scrX[uu], scrY[uu], scrX[vv], scrY[vv]);
                }

                pathNodeMark = new boolean[n];
                for (int v : a) if (v >= 0 && v < n) pathNodeMark[v] = true;
            }

            // Push: thick highlighted edge + delta bubble + arrow + edge label from shadow table.
            for (VisEvent ev : currentFrame.events) {
                if (!(ev instanceof Push)) continue;
                Push p = (Push) ev;

                int uu = p.u, vv = p.v;
                if (uu < 0 || vv < 0 || uu >= n || vv >= n) continue;

                // Highlight the pushed edge.
                g2.setColor(COLOR_PUSH);
                g2.setStroke(STROKE_THICK);
                g2.drawLine(scrX[uu], scrY[uu], scrX[vv], scrY[vv]);

                // Delta bubble at 1/4 of the segment.
                int px = (int) Math.round(scrX[uu] + (scrX[vv] - scrX[uu]) * 0.25);
                int py = (int) Math.round(scrY[uu] + (scrY[vv] - scrY[uu]) * 0.25);
                drawLabelBubble(g2, px, py, "+" + p.delta, BUBBLE_FILL, BUBBLE_EDGE, BUBBLE_TEXT);

                // Arrow head (two wings) at 3/4 of the segment.
                drawInlineArrow(g2, scrX[uu], scrY[uu], scrX[vv], scrY[vv], 0.75, 12.0, 6.0);

                // Edge label at mid-point:
                // "used/left" where left = origCap - used (based on the shadow table).
                EdgeView view = resolveEdgeViewForEvent(uu, vv);
                if (view != null) {
                    long used = view.info.used;
                    long cap = view.info.origCap;
                    long left = Math.max(0, cap - used);

                    int cx = (int) Math.round(scrX[uu] + (scrX[vv] - scrX[uu]) * 0.50);
                    int cy = (int) Math.round(scrY[uu] + (scrY[vv] - scrY[uu]) * 0.50);

                    // Offset label slightly along the normal to avoid sitting on top of the highlighted edge.
                    Offset o = edgeNormalOffset(scrX[uu], scrY[uu], scrX[vv], scrY[vv], -14);
                    drawLabelBubble(
                            g2, cx + o.dx, cy + o.dy,
                            used + "/" + left,
                            new Color(255, 255, 255, 235),
                            new Color(90, 90, 90, 200),
                            new Color(20, 20, 20)
                    );
                }
            }

            // Cut: draw dashed purple edges that cross from S to V\S (only original edges).
            for (VisEvent ev : currentFrame.events) {
                if (!(ev instanceof Cut)) continue;
                Cut c = (Cut) ev;

                boolean[] inS = c.inS;
                if (inS == null) continue;

                g2.setColor(COLOR_CUT);
                g2.setStroke(STROKE_DASH);

                for (int uu = 0; uu < n; uu++) {
                    for (Edge e : graph.adj(uu)) {
                        if (e.origCap <= 0) continue;
                        int vv = e.to;
                        if (uu < inS.length && vv < inS.length && inS[uu] && !inS[vv]) {
                            g2.drawLine(scrX[uu], scrY[uu], scrX[vv], scrY[vv]);
                        }
                    }
                }
            }

            g2.setStroke(saved);
        }

        // (3) Nodes: either colored by Levels, or default coloring for s/t/normal nodes.
        for (int u = 0; u < n; u++) {
            int cx = scrX[u], cy = scrY[u];
            Color fill;

            if (levels != null && u < levels.length && levels[u] >= 0) {
                fill = colorForLevel(levels[u], Lmax);
            } else {
                if (u == s) fill = COLOR_S;
                else if (u == t) fill = COLOR_T;
                else fill = COLOR_NODE;
            }

            g2.setColor(fill);
            g2.fillOval(cx - NODE_R, cy - NODE_R, 2 * NODE_R, 2 * NODE_R);

            g2.setColor(COLOR_OUTLINE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(cx - NODE_R, cy - NODE_R, 2 * NODE_R, 2 * NODE_R);
        }

        // (4) Node highlights: path nodes (blue ring) and relabeled nodes (orange ring).
        if (currentFrame != null && currentFrame.events != null) {
            if (pathNodeMark != null) {
                g2.setColor(COLOR_PATH_NODE);
                g2.setStroke(new BasicStroke(2.4f));
                for (int u = 0; u < n; u++) if (pathNodeMark[u]) {
                    int cx = scrX[u], cy = scrY[u];
                    int rr = NODE_R + 1;
                    g2.drawOval(cx - rr, cy - rr, rr * 2, rr * 2);
                }
            }

            g2.setColor(COLOR_RELABEL);
            g2.setStroke(new BasicStroke(3.0f));
            for (VisEvent ev : currentFrame.events) {
                if (!(ev instanceof Relabel)) continue;
                Relabel re = (Relabel) ev;
                int u = re.u;
                if (u < 0 || u >= n) continue;

                int cx = scrX[u], cy = scrY[u];
                int rr = NODE_R + 6;
                g2.drawOval(cx - rr, cy - rr, rr * 2, rr * 2);
            }
        }

        // Legend.
        drawLeftLegend(g2, levels != null && Lmax >= 0, Lmax);

        g2.dispose();
    }

    /* ====================== Helpers ====================== */

    private static long dirKey(int u, int v) {
        return (((long) u) << 32) ^ (v & 0xffffffffL);
    }

    private static long undirectedKey(int a, int b) {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        return (((long) lo) << 32) | (hi & 0xffffffffL);
    }

    /**
     * Maps a level index into a color gradient (blue/teal → yellow) for visual separation.
     */
    private Color colorForLevel(int lv, int Lmax) {
        if (lv < 0) return new Color(180, 180, 180);
        if (Lmax <= 0) return new Color(66, 133, 244);

        float t = lv / (float) Lmax;
        float hue = 0.58f + (0.12f - 0.58f) * t;
        return Color.getHSBColor(hue, 0.65f, 0.95f);
    }

    /**
     * Draws a small legend on the left side explaining node colors and (optionally) levels.
     */
    private void drawLeftLegend(Graphics2D g2, boolean showLevels, int Lmax) {
        Font base = g2.getFont();
        g2.setFont(base.deriveFont(Font.PLAIN, 12f));

        int x = 10, y = 16, sw = 14, sh = 14;

        g2.setColor(COLOR_S);
        g2.fillRect(x, y, sw, sh);
        g2.setColor(new Color(40, 40, 40, 220));
        g2.drawRect(x, y, sw, sh);
        g2.drawString("s (source)", x + sw + 6, y + sh - 3);
        y += 20;

        g2.setColor(COLOR_T);
        g2.fillRect(x, y, sw, sh);
        g2.setColor(new Color(40, 40, 40, 220));
        g2.drawRect(x, y, sw, sh);
        g2.drawString("t (sink)", x + sw + 6, y + sh - 3);
        y += 24;

        if (showLevels) {
            g2.setColor(new Color(40, 40, 40, 220));
            g2.drawString("Level color (L0..L" + Lmax + ")", x, y);
            y += 10;

            for (int lv = 0; lv <= Lmax; lv++) {
                Color c = colorForLevel(lv, Lmax);
                g2.setColor(c);
                g2.fillRect(x, y, sw, sh);
                g2.setColor(new Color(40, 40, 40, 220));
                g2.drawRect(x, y, sw, sh);
                g2.drawString("L" + lv, x + sw + 6, y + sh - 3);
                y += 18;
            }
        }

        g2.setFont(base);
    }

    private static final class Offset {
        final int dx, dy;
        Offset(int dx, int dy) { this.dx = dx; this.dy = dy; }
    }

    /**
     * Returns a pixel offset along the left normal of segment (x1,y1)->(x2,y2).
     * Used to move text labels away from the edge.
     */
    private Offset edgeNormalOffset(int x1, int y1, int x2, int y2, int pixels) {
        double dx = x2 - x1, dy = y2 - y1;
        double L = Math.hypot(dx, dy);
        if (L < 1e-6) return new Offset(0, 0);

        double nx = -(dy / L), ny = (dx / L);
        return new Offset((int) Math.round(nx * pixels), (int) Math.round(ny * pixels));
    }

    /**
     * Draws a "V-shaped" arrow head on segment (x1,y1)->(x2,y2) at a given interpolation factor t.
     *
     * @param t    position on the segment in [0,1]
     * @param len  length of the arrow head stem
     * @param wing half-width of the arrow head wings
     */
    private void drawInlineArrow(Graphics2D g2,
                                 int x1, int y1, int x2, int y2,
                                 double t, double len, double wing) {
        double dx = x2 - x1, dy = y2 - y1;
        double L = Math.hypot(dx, dy);
        if (L < 1e-6) return;

        double ux = dx / L, uy = dy / L; // tangent (unit)
        double nx = -uy, ny = ux;        // normal (unit)

        double px = x1 + dx * t, py = y1 + dy * t;
        double bx = px - ux * len, by = py - uy * len;

        int lx = (int) Math.round(bx + nx * wing);
        int ly = (int) Math.round(by + ny * wing);
        int rx = (int) Math.round(bx - nx * wing);
        int ry = (int) Math.round(by - ny * wing);

        int sx = (int) Math.round(px), sy = (int) Math.round(py);
        g2.drawLine(sx, sy, lx, ly);
        g2.drawLine(sx, sy, rx, ry);
    }

    /**
     * Draws a rounded label bubble centered at (x,y) containing the given text.
     */
    private void drawLabelBubble(Graphics2D g2, int x, int y, String text,
                                 Color fill, Color border, Color textColor) {
        if (text == null) return;

        FontMetrics fm = g2.getFontMetrics();
        int w = Math.max(1, fm.stringWidth(text));
        int h = fm.getAscent();
        int pad = 3, arc = 8;

        int rx = x - w / 2 - pad;
        int ry = y - h + fm.getDescent() - pad;
        int rw = w + pad * 2;
        int rh = h + pad * 2;

        g2.setColor(fill);
        g2.fillRoundRect(rx, ry, rw, rh, arc, arc);
        g2.setColor(border);
        g2.drawRoundRect(rx, ry, rw, rh, arc, arc);
        g2.setColor(textColor);
        g2.drawString(text, x - w / 2, y);
    }

    /* ====================== Event edge → original edge mapping ====================== */

    private static final class EdgeView {
        final EdgeInfo info;
        final boolean forward; // true: matches original u->v; false: represents a rollback on original v->u
        EdgeView(EdgeInfo info, boolean forward) { this.info = info; this.forward = forward; }
    }

    /**
     * For a push event on (u,v), resolve which original directed edge it corresponds to:
     * <ul>
     *   <li>If original u->v exists, return that (forward=true).</li>
     *   <li>Else if original v->u exists, return that (forward=false).</li>
     * </ul>
     * Returns null if neither direction corresponds to an original directed edge (rare in this project).
     */
    private EdgeView resolveEdgeViewForEvent(int u, int v) {
        long kF = dirKey(u, v);
        if (edgeState.containsKey(kF)) return new EdgeView(edgeState.get(kF), true);

        long kR = dirKey(v, u);
        if (edgeState.containsKey(kR)) return new EdgeView(edgeState.get(kR), false);

        return null;
    }
}
