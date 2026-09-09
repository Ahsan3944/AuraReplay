package com.ultraop.aurareplay.actor;

import com.ultraop.aurareplay.recording.snapshot.EntitySnapshot;

/** Immutable render-ready sample produced from a recording timeline position. */
public record ActorSample(
        EntitySnapshot source,
        ActorTransform transform,
        double timelinePosition
) {
    public boolean exists() {
        return source != null && source.exists();
    }
}
