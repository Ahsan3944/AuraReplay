package com.ultraop.aurareplay.actor;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Pure deterministic diff of two actor visual states. */
public final class ActorVisualStateDiff {
    public enum Change { FLAGS, EQUIPMENT, IDENTITY, EXISTENCE }

    private ActorVisualStateDiff() { }

    public static Set<Change> between(ActorVisualState previous, ActorVisualState current) {
        Objects.requireNonNull(current, "current");
        if (previous == null) return EnumSet.of(Change.FLAGS, Change.EQUIPMENT, Change.IDENTITY, Change.EXISTENCE);
        EnumSet<Change> changes = EnumSet.noneOf(Change.class);
        if (previous.flags() != current.flags()) changes.add(Change.FLAGS);
        if (!Objects.equals(previous.equipment(), current.equipment())) changes.add(Change.EQUIPMENT);
        if (!Objects.equals(previous.identity(), current.identity())) changes.add(Change.IDENTITY);
        if (previous.exists() != current.exists()) changes.add(Change.EXISTENCE);
        return changes;
    }
}
