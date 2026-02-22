package ega.gui.vis;


public final class Push implements VisEvent {
    public final int u;
    public final int v;
    public final long delta;

    public Push(int u, int v, long delta) {
        this.u = u;
        this.v = v;
        this.delta = delta;
    }
}
