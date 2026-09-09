package com.ultraop.aurareplay.timeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** High-level non-destructive timeline editing facade. */
public final class TimelineEditor {
    private final TimelineHistory history;

    public TimelineEditor() { this(new TimelineHistory()); }
    public TimelineEditor(TimelineHistory history) { this.history = Objects.requireNonNull(history); }
    public TimelineHistory history() { return history; }

    public void setRange(Timeline timeline, long in, long out) {
        execute(timeline, "Set range", t -> t.setRange(in, out));
    }

    public void setSpeed(Timeline timeline, double speed) {
        execute(timeline, "Set playback speed", t -> t.setPlaybackSpeed(speed));
    }

    public void setLoop(Timeline timeline, boolean loop) {
        execute(timeline, "Set loop", t -> t.setLoop(loop));
    }

    public void setReverse(Timeline timeline, boolean reverse) {
        execute(timeline, "Set reverse", t -> t.setReverse(reverse));
    }

    public void addMarker(Timeline timeline, TimelineMarker marker) {
        execute(timeline, "Add marker", t -> t.addMarker(marker));
    }

    public void removeMarker(Timeline timeline, String id) {
        execute(timeline, "Remove marker", t -> t.removeMarker(id));
    }

    public void addKeyframe(Timeline timeline, TimelineKeyframe keyframe) {
        execute(timeline, "Add keyframe", t -> t.addKeyframe(keyframe));
    }

    public void removeKeyframe(Timeline timeline, long tick, String channel) {
        execute(timeline, "Remove keyframe", t -> t.removeKeyframe(tick, channel));
    }

    /** Moves the editable time range and all timeline objects by the same amount. */
    public void offset(Timeline timeline, long deltaTicks) {
        TimelineState before = TimelineState.capture(timeline);
        TimelineState after = offsetState(before, deltaTicks);
        history.execute(timeline, new StateEdit("Offset timeline", before, after));
    }

    /** Scales timeline time around zero; useful for non-destructive retiming. */
    public void timeStretch(Timeline timeline, double factor) {
        if (!Double.isFinite(factor) || factor <= 0.0d) throw new IllegalArgumentException("factor must be finite and > 0");
        TimelineState before = TimelineState.capture(timeline);
        TimelineState after = stretchState(before, factor);
        history.execute(timeline, new StateEdit("Time stretch", before, after));
    }

    public boolean undo(Timeline timeline) { return history.undo(timeline); }
    public boolean redo(Timeline timeline) { return history.redo(timeline); }

    private void execute(Timeline timeline, String description, java.util.function.Consumer<Timeline> operation) {
        TimelineState before = TimelineState.capture(timeline);
        TimelineState after;
        Timeline copy = new Timeline();
        before.restore(copy);
        operation.accept(copy);
        after = TimelineState.capture(copy);
        history.execute(timeline, new StateEdit(description, before, after));
    }

    private static TimelineState offsetState(TimelineState state, long delta) {
        long duration = Math.max(0L, state.durationTicks() + delta);
        long in = clamp(state.inPoint() + delta, 0L, duration);
        long out = clamp(state.outPoint() + delta, in, duration);
        List<TimelineMarker> markers = state.markers().stream()
                .map(m -> new TimelineMarker(m.id(), Math.max(0L, m.tick() + delta), m.label())).toList();
        List<TimelineKeyframe> keyframes = state.keyframes().stream()
                .map(k -> new TimelineKeyframe(Math.max(0L, k.tick() + delta), k.channel(), k.value())).toList();
        return new TimelineState(duration, in, out, state.playbackSpeed(), state.loop(), state.reverse(), markers, keyframes);
    }

    private static TimelineState stretchState(TimelineState state, double factor) {
        long duration = Math.max(0L, Math.round(state.durationTicks() * factor));
        long in = Math.min(duration, Math.max(0L, Math.round(state.inPoint() * factor)));
        long out = Math.min(duration, Math.max(in, Math.round(state.outPoint() * factor)));
        List<TimelineMarker> markers = state.markers().stream()
                .map(m -> new TimelineMarker(m.id(), Math.max(0L, Math.round(m.tick() * factor)), m.label())).toList();
        List<TimelineKeyframe> keyframes = state.keyframes().stream()
                .map(k -> new TimelineKeyframe(Math.max(0L, Math.round(k.tick() * factor)), k.channel(), k.value())).toList();
        return new TimelineState(duration, in, out, state.playbackSpeed() / factor, state.loop(), state.reverse(), markers, keyframes);
    }

    private static long clamp(long value, long min, long max) { return Math.max(min, Math.min(max, value)); }

    private static final class StateEdit implements TimelineEdit {
        private final String description;
        private final TimelineState before;
        private final TimelineState after;
        private StateEdit(String description, TimelineState before, TimelineState after) {
            this.description = description; this.before = before; this.after = after;
        }
        @Override public void apply(Timeline timeline) { after.restore(timeline); }
        @Override public void undo(Timeline timeline) { before.restore(timeline); }
        @Override public String description() { return description; }
    }
}
