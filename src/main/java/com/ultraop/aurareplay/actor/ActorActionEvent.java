package com.ultraop.aurareplay.actor;

import java.util.Objects;

/** Immutable point event for an actor animation/action timeline. */
public record ActorActionEvent(long tick, ActorAction action) {
    public ActorActionEvent {
        if (tick < 0) throw new IllegalArgumentException("tick must be >= 0");
        Objects.requireNonNull(action, "action");
    }
}
