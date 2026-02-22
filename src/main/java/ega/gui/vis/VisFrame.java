package ega.gui.vis;

import java.util.ArrayList;
import java.util.List;


public class VisFrame {

    public final List<VisEvent> events = new ArrayList<>();

    public void add(VisEvent e) { events.add(e); }
}
