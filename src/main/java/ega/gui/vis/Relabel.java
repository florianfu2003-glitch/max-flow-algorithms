package ega.gui.vis;


public final class Relabel implements VisEvent {
    public final int u;
    public final int oldH;
    public final int newH;

    public Relabel(int u, int oldH, int newH) {
        this.u = u;
        this.oldH = oldH;
        this.newH = newH;
    }
}
