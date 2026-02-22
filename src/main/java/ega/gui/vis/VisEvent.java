package ega.gui.vis;

public sealed interface VisEvent permits Path, Push, Relabel, Levels, Cut, Clear { }