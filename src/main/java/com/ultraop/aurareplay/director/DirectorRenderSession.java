package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;

import java.util.Objects;
import java.util.function.Function;

/**
 * Drives deterministic export frames without coupling the director to an image encoder.
 * The supplied sampler should apply the sampled scene transform to the in-game renderer
 * before returning it; the sink can then hand the frame to a client capture/encoder bridge.
 */
public final class DirectorRenderSession {
    public enum State { READY, RUNNING, COMPLETED, STOPPED }

    private final DirectorExportSpec spec;
    private final Function<Double, CameraTransform> sampler;
    private final DirectorFrameSink sink;
    private long nextFrame;
    private State state = State.READY;

    public DirectorRenderSession(DirectorExportSpec spec,
                                 Function<Double, CameraTransform> sampler,
                                 DirectorFrameSink sink) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    public DirectorExportSpec spec() { return spec; }
    public State state() { return state; }
    public long nextFrameIndex() { return nextFrame; }
    public boolean completed() { return state == State.COMPLETED; }

    public void start() {
        if (state != State.READY) throw new IllegalStateException("session is not ready");
        state = State.RUNNING;
    }

    /** Captures exactly one deterministic frame. Returns false when no frame remains. */
    public boolean captureNext() {
        if (state == State.READY) start();
        if (state != State.RUNNING) return false;
        if (nextFrame >= spec.frameCount()) {
            state = State.COMPLETED;
            return false;
        }
        DirectorFrame frame = DirectorExportPlanner.sample(spec, nextFrame, sampler);
        sink.accept(frame);
        nextFrame++;
        if (nextFrame >= spec.frameCount()) state = State.COMPLETED;
        return true;
    }

    /** Captures all remaining frames synchronously. */
    public long captureAll() {
        long captured = 0L;
        while (captureNext()) captured++;
        return captured;
    }

    public void stop() {
        if (state == State.RUNNING || state == State.READY) state = State.STOPPED;
    }
}
