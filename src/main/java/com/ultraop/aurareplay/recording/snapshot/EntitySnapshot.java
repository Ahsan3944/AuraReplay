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
        int flags
) {}
