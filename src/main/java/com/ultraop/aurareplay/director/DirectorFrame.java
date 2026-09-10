package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;

import java.util.Objects;

/** One deterministic sampled director frame, independent of the output encoder. */
public record DirectorFrame(long frameIndex, double sceneTick, CameraTransform camera) {
    public DirectorFrame {
        if (frameIndex < 0) throw new IllegalArgumentException("frameIndex must be >= 0");
        if (!Double.isFinite(sceneTick)) throw new IllegalArgumentException("sceneTick must be finite");
        Objects.requireNonNull(camera, "camera");
    }
}
