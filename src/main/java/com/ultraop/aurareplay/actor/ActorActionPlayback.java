package com.ultraop.aurareplay.actor;

import java.util.List;
import java.util.Objects;

/** Resolves sparse actor actions against a playback timeline without mutating the timeline. */
public final class ActorActionPlayback {
    private final ActorActionTimeline timeline;
    private long lastTick = -1L;

    public ActorActionPlayback(ActorActionTimeline timeline) {
        this.timeline = Objects.requireNonNull(timeline, "timeline");
    }

    public ActorActionTimeline timeline() { return timeline; }
    public long lastTick() { return lastTick; }

    /** Returns actions crossed by forward playback in the half-open interval (previous, tick]. */
    public List<ActorActionEvent> advance(long tick) {
        if (tick < 0) throw new IllegalArgumentException("tick must be >= 0");
        if (tick < lastTick) {
            lastTick = tick;
            return List.of();
        }
        List<ActorActionEvent> events = timeline.between(lastTick + 1L, tick + 1L);
        lastTick = tick;
        return events;
    }

    /** Resets the cursor so the next advance includes events from tick zero. */
    public void reset() { lastTick = -1L; }

    /** Seeks without emitting events. */
    public void seek(long tick) {
        if (tick < -1L) throw new IllegalArgumentException("tick must be >= -1");
        lastTick = tick;
    }
}
