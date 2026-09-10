package com.ultraop.aurareplay.actor;

import com.ultraop.aurareplay.recording.snapshot.EntityIdentitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.EquipmentSnapshot;

import java.util.Objects;

/** Immutable sampled visual state used to diff actor rendering between frames. */
public record ActorVisualState(
        int flags,
        EquipmentSnapshot equipment,
        EntityIdentitySnapshot identity,
        boolean exists
) {
    public ActorVisualState {
        if (!exists && (equipment != null || identity != null)) {
            // Keep non-existent samples representable while avoiding mutable state.
        }
    }

    public static ActorVisualState from(ActorSample sample) {
        Objects.requireNonNull(sample, "sample");
        return new ActorVisualState(sample.flags(), sample.equipment(), sample.identity(), sample.exists());
    }
}
