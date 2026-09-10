package com.ultraop.aurareplay.actor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable, deterministically ordered action timeline. */
public final class ActorActionTimeline {
    private final List<ActorActionEvent> events;

    public ActorActionTimeline(Iterable<ActorActionEvent> events) {
        Objects.requireNonNull(events, "events");
        List<ActorActionEvent> copy = new ArrayList<>();
        for (ActorActionEvent event : events) copy.add(Objects.requireNonNull(event, "event"));
        copy.sort(Comparator.comparingLong(ActorActionEvent::tick).thenComparing(e -> e.action().ordinal()));
        for (int i = 1; i < copy.size(); i++) {
            if (copy.get(i).equals(copy.get(i - 1))) {
                throw new IllegalArgumentException("duplicate action event at tick " + copy.get(i).tick());
            }
        }
        this.events = List.copyOf(copy);
    }

    public List<ActorActionEvent> events() { return events; }

    public List<ActorActionEvent> at(long tick) {
        if (tick < 0) throw new IllegalArgumentException("tick must be >= 0");
        return events.stream().filter(e -> e.tick() == tick).toList();
    }

    public List<ActorActionEvent> between(long startInclusive, long endExclusive) {
        if (startInclusive < 0 || endExclusive < startInclusive)
            throw new IllegalArgumentException("invalid range");
        return events.stream().filter(e -> e.tick() >= startInclusive && e.tick() < endExclusive).toList();
    }
}
