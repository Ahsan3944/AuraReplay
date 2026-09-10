package com.ultraop.aurareplay.actor;

import com.ultraop.aurareplay.recording.EntityFlags;
import com.ultraop.aurareplay.recording.snapshot.EntitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.EntityIdentitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.EquipmentSnapshot;

/** Immutable render-ready sample produced from a recording timeline position. */
public record ActorSample(
        EntitySnapshot source,
        ActorTransform transform,
        double timelinePosition
) {
    public boolean exists() {
        return source != null && source.exists();
    }

    public EquipmentSnapshot equipment() {
        return source == null ? null : source.equipment();
    }

    public EntityIdentitySnapshot identity() {
        return source == null ? null : source.identity();
    }

    public int flags() {
        return source == null ? 0 : source.flags();
    }

    public boolean sneaking() { return (flags() & EntityFlags.SNEAKING) != 0; }
    public boolean swimming() { return (flags() & EntityFlags.SWIMMING) != 0; }
    public boolean gliding() { return (flags() & EntityFlags.GLIDING) != 0; }
    public boolean invisible() { return (flags() & EntityFlags.INVISIBLE) != 0; }
    public boolean glowing() { return (flags() & EntityFlags.GLOWING) != 0; }
    public boolean onFire() { return (flags() & EntityFlags.ON_FIRE) != 0; }
    public boolean sprinting() { return (flags() & EntityFlags.SPRINTING) != 0; }
}
