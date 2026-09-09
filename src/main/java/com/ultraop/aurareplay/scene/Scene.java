package com.ultraop.aurareplay.scene;

import com.ultraop.aurareplay.actor.ActorId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable scene identity with an editable ordered actor composition. */
public final class Scene {
    private final String name;
    private final List<ActorId> actorIds = new ArrayList<>();

    public Scene(String name) {
        this.name = normalizeName(name);
    }

    public String name() { return name; }

    public List<ActorId> actorIds() { return List.copyOf(actorIds); }

    public boolean addActor(ActorId actorId) {
        Objects.requireNonNull(actorId, "actorId");
        if (actorIds.contains(actorId)) return false;
        actorIds.add(actorId);
        return true;
    }

    public boolean removeActor(ActorId actorId) {
        return actorIds.remove(actorId);
    }

    public void clearActors() { actorIds.clear(); }

    private static String normalizeName(String name) {
        Objects.requireNonNull(name, "name");
        String value = name.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("scene name cannot be empty");
        return value;
    }
}
