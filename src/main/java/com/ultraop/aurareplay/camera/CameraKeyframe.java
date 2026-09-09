package com.ultraop.aurareplay.camera;

import java.util.Objects;

/** A camera transform sampled at a scene timeline tick. */
public record CameraKeyframe(long tick, CameraTransform transform) {
    public CameraKeyframe {
        if (tick < 0) throw new IllegalArgumentException("tick must be >= 0");
        Objects.requireNonNull(transform, "transform");
    }
}
