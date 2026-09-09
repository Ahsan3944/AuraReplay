package com.ultraop.aurareplay.timeline;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Bounded command history for non-destructive timeline editing. */
public final class TimelineHistory {
    private final int capacity;
    private final Deque<TimelineEdit> undoStack = new ArrayDeque<>();
    private final Deque<TimelineEdit> redoStack = new ArrayDeque<>();

    public TimelineHistory() { this(100); }

    public TimelineHistory(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1");
        this.capacity = capacity;
    }

    public void execute(Timeline timeline, TimelineEdit edit) {
        Objects.requireNonNull(timeline);
        Objects.requireNonNull(edit);
        edit.apply(timeline);
        undoStack.push(edit);
        redoStack.clear();
        while (undoStack.size() > capacity) undoStack.removeLast();
    }

    public boolean undo(Timeline timeline) {
        if (undoStack.isEmpty()) return false;
        TimelineEdit edit = undoStack.pop();
        edit.undo(timeline);
        redoStack.push(edit);
        return true;
    }

    public boolean redo(Timeline timeline) {
        if (redoStack.isEmpty()) return false;
        TimelineEdit edit = redoStack.pop();
        edit.apply(timeline);
        undoStack.push(edit);
        return true;
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }
    public int undoDepth() { return undoStack.size(); }
    public int redoDepth() { return redoStack.size(); }
    public void clear() { undoStack.clear(); redoStack.clear(); }
}
