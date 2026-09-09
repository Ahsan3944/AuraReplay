package com.ultraop.aurareplay.recording.snapshot;

import com.ultraop.aurareplay.recording.action.Action;

import java.util.List;

public record TickSnapshot(
        long tick,
        List<EntitySnapshot> entities,
        List<Action> actions
) {
    public TickSnapshot {
        entities = List.copyOf(entities);
        actions = List.copyOf(actions);
    }

    public TickSnapshot(long tick, List<EntitySnapshot> entities) {
        this(tick, entities, List.of());
    }
}
