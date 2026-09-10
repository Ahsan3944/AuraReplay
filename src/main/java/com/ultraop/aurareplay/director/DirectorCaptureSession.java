package com.ultraop.aurareplay.director;

import java.util.Objects;

/** Validates capture lifecycle and deterministic frame ordering before forwarding frames. */
public final class DirectorCaptureSession {
    public enum State { READY, RUNNING, COMPLETED, CANCELLED, FAILED }
    private final DirectorExportSpec spec;
    private final DirectorCaptureSink sink;
    private long nextFrame;
    private State state = State.READY;
    private Throwable failure;

    public DirectorCaptureSession(DirectorExportSpec spec, DirectorCaptureSink sink) { this.spec = Objects.requireNonNull(spec, "spec"); this.sink = Objects.requireNonNull(sink, "sink"); }
    public DirectorExportSpec spec() { return spec; }
    public State state() { return state; }
    public long nextFrameIndex() { return nextFrame; }
    public long capturedFrames() { return nextFrame; }
    public Throwable failure() { return failure; }
    public void start() { startAt(0L); }

    /** Starts capture at a previously checkpointed frame. */
    public void startAt(long frameIndex) {
        if (state != State.READY) throw new IllegalStateException("capture session is not ready");
        if (frameIndex < 0 || frameIndex > spec.frameCount()) throw new IllegalArgumentException("frameIndex must be within export frame range");
        nextFrame = frameIndex;
        sink.start(spec);
        state = frameIndex == spec.frameCount() ? State.COMPLETED : State.RUNNING;
    }

    public void accept(DirectorFrame frame) {
        Objects.requireNonNull(frame, "frame");
        if (state == State.READY) start();
        if (state != State.RUNNING) throw new IllegalStateException("capture session is not running");
        if (frame.frameIndex() != nextFrame) throw new IllegalArgumentException("expected frame " + nextFrame + " but received " + frame.frameIndex());
        double expectedTick = spec.tickForFrame(nextFrame);
        if (Math.abs(frame.sceneTick() - expectedTick) > 1e-9) throw new IllegalArgumentException("frame " + nextFrame + " has wrong scene tick: " + frame.sceneTick());
        try { sink.accept(frame); nextFrame++; if (nextFrame >= spec.frameCount()) complete(); }
        catch (Throwable ex) { fail(ex); throw ex; }
    }

    public void complete() {
        if (state == State.COMPLETED) return;
        if (state != State.RUNNING) throw new IllegalStateException("capture session is not running");
        if (nextFrame != spec.frameCount()) throw new IllegalStateException("capture is incomplete: " + nextFrame + "/" + spec.frameCount());
        sink.complete(); state = State.COMPLETED;
    }
    public void cancel() { if (state != State.READY && state != State.RUNNING) return; try { sink.cancel(); } finally { state = State.CANCELLED; } }
    public void fail(Throwable error) { Objects.requireNonNull(error, "error"); if (state == State.COMPLETED || state == State.CANCELLED) return; failure = error; try { sink.fail(error); } finally { state = State.FAILED; } }
}
