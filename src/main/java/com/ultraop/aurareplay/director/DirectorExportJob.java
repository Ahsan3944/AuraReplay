package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** Incremental, main-thread-safe frame collection for a Director export. */
public final class DirectorExportJob {
    public enum State { READY, RUNNING, COMPLETED, CANCELLED, FAILED }

    private final DirectorExportSpec spec;
    private final Function<Double, CameraTransform> sampler;
    private final Consumer<DirectorFrame> frameConsumer;
    private final List<DirectorFrame> frames;
    private long nextFrame;
    private State state = State.READY;
    private Throwable failure;

    public DirectorExportJob(DirectorExportSpec spec, Function<Double, CameraTransform> sampler) {
        this(spec, sampler, null);
    }

    public DirectorExportJob(DirectorExportSpec spec,
                             Function<Double, CameraTransform> sampler,
                             Consumer<DirectorFrame> frameConsumer) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.frameConsumer = frameConsumer;
        this.frames = new ArrayList<>((int) Math.min(spec.frameCount(), Integer.MAX_VALUE));
    }

    public DirectorExportSpec spec() { return spec; }
    public State state() { return state; }
    public long nextFrameIndex() { return nextFrame; }
    public long capturedFrames() { return frames.size(); }
    public Throwable failure() { return failure; }

    public void start() {
        if (state != State.READY) throw new IllegalStateException("job is not ready");
        state = State.RUNNING;
    }

    /** Captures at most maxFrames and returns the number captured during this step. */
    public int step(int maxFrames) {
        if (maxFrames <= 0) throw new IllegalArgumentException("maxFrames must be positive");
        if (state == State.READY) start();
        if (state != State.RUNNING) return 0;

        int captured = 0;
        try {
            while (captured < maxFrames && nextFrame < spec.frameCount()) {
                DirectorFrame frame = DirectorExportPlanner.sample(spec, nextFrame, sampler);
                frames.add(frame);
                if (frameConsumer != null) frameConsumer.accept(frame);
                nextFrame++;
                captured++;
            }
            if (nextFrame >= spec.frameCount()) state = State.COMPLETED;
            return captured;
        } catch (Throwable ex) {
            failure = ex;
            state = State.FAILED;
            return captured;
        }
    }

    public List<DirectorFrame> frames() { return List.copyOf(frames); }

    public void cancel() {
        if (state == State.READY || state == State.RUNNING) state = State.CANCELLED;
    }
}
