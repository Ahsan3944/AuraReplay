package com.ultraop.aurareplay.recording.action;

import com.ultraop.aurareplay.actor.ActorAction;

import java.util.Objects;
import java.util.UUID;

/** Recorded actor animation event associated with a source entity UUID. */
public record ActorActionRecord(long tick, UUID actor, ActorAction action) implements Action {
    public ActorActionRecord {
        if (tick < 0) throw new IllegalArgumentException("tick must be >= 0");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
    }
}
