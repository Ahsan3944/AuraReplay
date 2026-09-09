package com.ultraop.aurareplay.recording;

import org.bukkit.entity.EntityType;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable reference to one entity stream inside an immutable recording.
 * UUID is preferred; entity id is retained for recordings where UUID is unavailable.
 */
public record RecordingSource(
        UUID uuid,
        Integer entityId,
        EntityType type,
        String displayName
) {
    public RecordingSource {
        if (uuid == null && entityId == null) {
            throw new IllegalArgumentException("recording source requires uuid or entityId");
        }
        Objects.requireNonNull(type, "type");
        displayName = displayName == null || displayName.isBlank() ? type.name() : displayName;
    }

    public String key() {
        return uuid != null ? "uuid:" + uuid : "id:" + entityId;
    }
}
