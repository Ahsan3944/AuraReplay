package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.Function;

/**
 * Tick-driven export session that emits deterministic frames at the requested FPS.
 * It applies each sampled frame to the viewer through the in-game render bridge and
 * optionally forwards the same ordered frames to a capture sink.
 */
public final class DirectorRealtimeRenderSession {
    public enum State { READY, RUNNING, COMPLETED, STOPPED, FAILED }

    private final DirectorExportSpec spec;
    private final DirectorInGameRenderBridge bridge;
    private final Player viewer;
    private final Function<Double, CameraTransform> sampler;
    private final DirectorRealtimeRenderScheduler scheduler;
    private final DirectorCaptureSession capture;
    private State state = State.READY;
    private Throwable failure;

    public DirectorRealtimeRenderSession(DirectorExportSpec spec,
                                         DirectorInGameRenderBridge bridge,
                                         Player viewer,
                                         Function<Double, CameraTransform> sampler) {
        this(spec, bridge, viewer, sampler, null);
    }

    public DirectorRealtimeRenderSession(DirectorExportSpec spec,
                                         DirectorInGameRenderBridge bridge,
                                         Player viewer,
                                         Function<Double, CameraTransform> sampler,
                                         DirectorCaptureSink captureSink) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.scheduler = new DirectorRealtimeRenderScheduler(spec.fps());
        this.capture = captureSink == null ? null : new DirectorCaptureSession(spec, captureSink);
    }

    public DirectorExportSpec spec() { return spec; }
    public State state() { return state; }
    public long emittedFrames() { return scheduler.emittedFrames(); }
    public long elapsedTicks() { return scheduler.elapsedTicks(); }
    public Throwable failure() { return failure; }
    public DirectorCaptureSession capture() { return capture; }

    public void start() {
        if (state != State.READY) throw new IllegalStateException("session is not ready");
        try {
            if (capture != null) capture.start();
            state = State.RUNNING;
        } catch (Throwable ex) {
            failure = ex;
            state = State.FAILED;
            if (capture != null) capture.fail(ex);
            throw ex;
        }
    }

    /** Advances one Minecraft tick and emits every frame whose presentation time has arrived. */
    public int tick() {
        if (state == State.READY) start();
        if (state != State.RUNNING) return 0;

        int due = scheduler.advance();
        int emitted = 0;
        try {
            while (emitted < due && scheduler.emittedFrames() < spec.frameCount()) {
                long frameIndex = scheduler.emittedFrames();
                DirectorFrame frame = DirectorExportPlanner.sample(spec, frameIndex, sampler);
                if (!bridge.render(viewer, frame)) {
                    stopCapture();
                    state = State.STOPPED;
                    break;
                }
                if (capture != null) capture.accept(frame);
                scheduler.markEmitted(1);
                emitted++;
            }
        } catch (Throwable ex) {
            failure = ex;
            state = State.FAILED;
            if (capture != null) capture.fail(ex);
        }

        if (state == State.RUNNING && scheduler.emittedFrames() >= spec.frameCount()) {
            state = State.COMPLETED;
        }
        return emitted;
    }

    public void stop() {
        if (state == State.READY || state == State.RUNNING) {
            stopCapture();
            state = State.STOPPED;
        }
    }

    private void stopCapture() {
        if (capture != null) capture.cancel();
    }
}
