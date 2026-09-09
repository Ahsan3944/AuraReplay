package com.ultraop.aurareplay.camera;

import java.util.Objects;

/** Viewer-local camera playback state. */
public final class CameraPlaybackSession {
    private final CameraDefinition camera;
    private double tick;
    private boolean active;

    public CameraPlaybackSession(CameraDefinition camera) {
        this.camera = Objects.requireNonNull(camera, "camera");
    }

    public CameraDefinition camera() { return camera; }
    public double tick() { return tick; }
    public boolean active() { return active; }

    public void start() { tick = 0.0; active = true; }
    public void stop() { active = false; }
    public void setTick(double tick) { this.tick = Math.max(0.0, tick); }

    public CameraTransform sample() { return camera.sample(tick); }

    public boolean advance(double deltaTicks) {
        if (!active) return false;
        tick = Math.max(0.0, tick + Math.max(0.0, deltaTicks));
        return true;
    }
}
