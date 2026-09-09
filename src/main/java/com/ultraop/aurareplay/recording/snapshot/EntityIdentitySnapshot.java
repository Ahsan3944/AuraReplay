package com.ultraop.aurareplay.recording.snapshot;

import java.util.Objects;
import java.util.UUID;

/** Immutable visual identity captured with an entity snapshot. */
public record EntityIdentitySnapshot(
        UUID profileUuid,
        String profileName,
        String skinTextureValue,
        String skinTextureSignature
) {
    public EntityIdentitySnapshot {
        Objects.requireNonNull(profileUuid, "profileUuid");
        profileName = profileName == null ? "AuraActor" : profileName;
    }

    public static EntityIdentitySnapshot of(UUID uuid, String name, String texture, String signature) {
        return new EntityIdentitySnapshot(uuid, name, texture, signature);
    }
}
