package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.recording.snapshot.EntityIdentitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.EntitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds a stable source catalog from an immutable recording without modifying it.
 */
public final class RecordingSourceCatalog {
    public List<RecordingSource> sources(Recording recording) {
        if (recording == null) throw new IllegalArgumentException("recording cannot be null");

        Map<String, RecordingSource> sources = new LinkedHashMap<>();
        for (TickSnapshot frame : recording.frames()) {
            for (EntitySnapshot entity : frame.entities()) {
                if (entity.uuid() == null && entity.entityId() < 0) continue;
                RecordingSource source = toSource(entity);
                sources.putIfAbsent(source.key(), source);
            }
        }
        return List.copyOf(sources.values());
    }

    public List<RecordingSource> sources(Recording recording, java.util.function.Predicate<EntitySnapshot> filter) {
        if (filter == null) throw new IllegalArgumentException("filter cannot be null");
        List<RecordingSource> result = new ArrayList<>();
        for (RecordingSource source : sources(recording)) {
            if (contains(recording, source, filter)) result.add(source);
        }
        return List.copyOf(result);
    }

    public java.util.Optional<RecordingSource> find(Recording recording, String key) {
        if (key == null) return java.util.Optional.empty();
        return sources(recording).stream().filter(source -> source.key().equalsIgnoreCase(key)).findFirst();
    }

    private boolean contains(Recording recording, RecordingSource source, java.util.function.Predicate<EntitySnapshot> filter) {
        for (TickSnapshot frame : recording.frames()) {
            for (EntitySnapshot entity : frame.entities()) {
                if (matches(source, entity) && filter.test(entity)) return true;
            }
        }
        return false;
    }

    private RecordingSource toSource(EntitySnapshot entity) {
        EntityIdentitySnapshot identity = entity.identity();
        String displayName = identity == null ? entity.type().name() : identity.profileName();
        return new RecordingSource(entity.uuid(), entity.entityId(), entity.type(), displayName);
    }

    private boolean matches(RecordingSource source, EntitySnapshot entity) {
        if (source.uuid() != null && source.uuid().equals(entity.uuid())) return true;
        return source.uuid() == null && source.entityId() != null && source.entityId().intValue() == entity.entityId();
    }
}
