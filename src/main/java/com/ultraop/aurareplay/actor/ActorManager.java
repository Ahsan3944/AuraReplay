package com.ultraop.aurareplay.actor;

import com.ultraop.aurareplay.recording.Recording;
import com.ultraop.aurareplay.recording.RecordingSource;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ActorManager {
    private final Map<ActorId, ActorDefinition> actors = new ConcurrentHashMap<>();

    public ActorDefinition create(Recording recording, ActorTransform transform) {
        ActorDefinition actor = new ActorDefinition(ActorId.random(), recording, transform);
        actors.put(actor.id(), actor);
        return actor;
    }

    public ActorDefinition create(Recording recording, ActorTransform transform, UUID sourceEntityUuid, Integer sourceEntityId) {
        ActorDefinition actor = new ActorDefinition(ActorId.random(), recording, transform, sourceEntityUuid, sourceEntityId);
        actors.put(actor.id(), actor);
        return actor;
    }

    /** Creates an actor instance bound to one entity stream in a shared recording. */
    public ActorDefinition createFromSource(Recording recording, RecordingSource source, ActorTransform transform) {
        if (recording == null) throw new IllegalArgumentException("recording cannot be null");
        if (source == null) throw new IllegalArgumentException("source cannot be null");
        return create(recording, transform, source.uuid(), source.entityId());
    }

    /** Registers a persisted actor using its stable ID. */
    public void register(ActorDefinition actor) {
        if (actor == null) throw new IllegalArgumentException("actor cannot be null");
        actors.put(actor.id(), actor);
    }

    public Optional<ActorDefinition> get(ActorId id) { return Optional.ofNullable(actors.get(id)); }
    public Optional<ActorDefinition> get(UUID id) { return get(new ActorId(id)); }
    public boolean remove(ActorId id) { return actors.remove(id) != null; }
    public Collection<ActorDefinition> all() { return actors.values().stream().toList(); }
    public void clear() { actors.clear(); }
}
