package ega.gui.vis;

public final class Cut implements VisEvent {
    public final boolean[] inS;

    public Cut(boolean[] inS) {
        this.inS = inS;
    }
}
