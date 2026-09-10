package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorSample;
import com.ultraop.aurareplay.camera.CameraTransform;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable deterministic snapshot of one complete Director render frame. */
public record DirectorRenderFrame(
        long frameIndex,
        double sceneTick,
        CameraTransform camera,
        Map<ActorId, ActorSample> actors
) {
    public DirectorRenderFrame {
        if (frameIndex < 0) throw new IllegalArgumentException("frameIndex must be >= 0");
        if (!Double.isFinite(sceneTick)) throw new IllegalArgumentException("sceneTick must be finite");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(actors, "actors");
        actors = Collections.unmodifiableMap(new LinkedHashMap<>(actors));
    }
}
