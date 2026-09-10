package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.Function;

/** Tick-driven director export bridge that emits deterministic frames at the configured FPS. */
public final class DirectorRealtimeRenderSession2 {
    public enum State { READY, RUNNING, COMPLETED, STOPPED }

    private final DirectorExportSpec spec;
    private final DirectorInGameRenderBridge bridge;
    private final Player viewer;
    private final Function<Double, CameraTransform> sampler;
    private long emittedFrames;
    private long elapsedTicks;
    private State state = State.READY;

    public DirectorRealtimeRenderSession2(DirectorExportSpec spec, DirectorInGameRenderBridge bridge,
                                          Player viewer, Function<Double, CameraTransform> sampler) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
    }

    public DirectorExportSpec spec() { return spec; }
    public State state() { return state; }
    public long emittedFrames() { return emittedFrames; }
    public long elapsedTicks() { return elapsedTicks; }

    public void start() {
        if (state != State.READY) throw new IllegalStateException("session is not ready");
        state = State.RUNNING;
    }

    public int tick() {
        if (state == State.READY) start();
        if (state != State.RUNNING) return 0;
        elapsedTicks++;
        int emitted = 0;
        while (emittedFrames < spec.frameCount()
                && emittedFrames * 20.0 <= elapsedTicks * (double) spec.fps()) {
            long frameIndex = emittedFrames;
            DirectorFrame frame = DirectorExportPlanner.sample(spec, frameIndex, sampler);
            if (!bridge.render(viewer, frame)) {
                state = State.STOPPED;
                break;
            }
            emittedFrames++;
            emitted++;
        }
        if (emittedFrames >= spec.frameCount()) state = State.COMPLETED;
        return emitted;
    }

    public void stop() {
        if (state == State.READY || state == State.RUNNING) state = State.STOPPED;
    }
}
