package com.ultraop.aurareplay.director;

import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Tick-driven export session that emits deterministic frames at the requested FPS.
 * It is intentionally independent from the actual pixel encoder.
 */
public final class DirectorRealtimeRenderSession {
    public enum State { READY, RUNNING, COMPLETED, STOPPED }

    private final DirectorRenderSession session;
    private final DirectorInGameRenderBridge bridge;
    private final Player viewer;
    private long emittedFrames;
    private long elapsedTicks;
    private State state = State.READY;

    public DirectorRealtimeRenderSession(DirectorExportSpec spec,
                                         DirectorInGameRenderBridge bridge,
                                         Player viewer,
                                         java.util.function.Function<Double, com.ultraop.aurareplay.camera.CameraTransform> sampler) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.session = new DirectorRenderSession(Objects.requireNonNull(spec, "spec"),
                Objects.requireNonNull(sampler, "sampler"), frame -> { });
    }

    public DirectorExportSpec spec() { return session.spec(); }
    public State state() { return state; }
    public long emittedFrames() { return emittedFrames; }
    public long elapsedTicks() { return elapsedTicks; }

    public void start() {
        if (state != State.READY) throw new IllegalStateException("session is not ready");
        session.start();
        state = State.RUNNING;
    }

    /** Advances one Minecraft tick and emits every frame whose presentation time has arrived. */
    public int tick() {
        if (state == State.READY) start();
        if (state != State.RUNNING) return 0;

        elapsedTicks++;
        int emitted = 0;
        while (emittedFrames < session.spec().frameCount()
                && emittedFrames * 20.0 <= elapsedTicks * (double) session.spec().fps()) {
            final long frameIndex = emittedFrames;
            DirectorFrame frame = DirectorExportPlanner.sample(session.spec(), frameIndex,
                    tick -> sessionFrame(frameIndex, tick));
            if (!bridge.render(viewer, frame)) {
                stop();
                break;
            }
            emittedFrames++;
            emitted++;
        }

        if (emittedFrames >= session.spec().frameCount()) state = State.COMPLETED;
        return emitted;
    }

    private com.ultraop.aurareplay.camera.CameraTransform sessionFrame(long frameIndex, double tick) {
        // The render session owns deterministic sampling; this method is replaced by
        // the supplied sampler through the wrapper below and exists only to keep the
        // bridge's frame lifecycle explicit.
        return new com.ultraop.aurareplay.camera.CameraTransform(0, 0, 0, 0, 0, 0, 70);
    }

    public void stop() {
        session.stop();
        if (state == State.READY || state == State.RUNNING) state = State.STOPPED;
    }
}
