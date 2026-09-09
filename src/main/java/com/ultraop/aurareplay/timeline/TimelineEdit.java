package com.ultraop.aurareplay.timeline;

/** A reversible, non-destructive timeline edit. */
public interface TimelineEdit {
    void apply(Timeline timeline);
    void undo(Timeline timeline);
    String description();
}
