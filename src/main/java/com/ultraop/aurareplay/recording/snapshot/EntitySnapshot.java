package com.ultraop.aurareplay.recording.snapshot;

import org.bukkit.entity.EntityType;

import java.util.UUID;

public record EntitySnapshot(
        int entityId,
        UUID uuid,
        EntityType type,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        double velocityX,
        double velocityY,
        double velocityZ,
        int flags,
        EquipmentSnapshot equipment,
        EntityIdentitySnapshot identity,
        boolean exists
) {
    public EntitySnapshot(
            int entityId,
            UUID uuid,
            EntityType type,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            double velocityX,
            double velocityY,
            double velocityZ,
            int flags
    ) {
        this(entityId, uuid, type, x, y, z, yaw, pitch,
                velocityX, velocityY, velocityZ, flags, null, null, true);
    }

    public EntitySnapshot(
            int entityId,
            UUID uuid,
            EntityType type,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            double velocityX,
            double velocityY,
            double velocityZ,
            int flags,
            EquipmentSnapshot equipment,
            boolean exists
    ) {
        this(entityId, uuid, type, x, y, z, yaw, pitch,
                velocityX, velocityY, velocityZ, flags, equipment, null, exists);
    }
}
