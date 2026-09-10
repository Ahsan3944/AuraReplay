package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import java.util.Objects;
import java.util.function.Function;

/** Deterministic FPS scheduler plus frame sampling; pixel capture remains a client bridge concern. */
public final class DirectorRealtimeRenderSessionFinal {
    private final DirectorExportSpec spec;
    private final DirectorRealtimeRenderScheduler scheduler;
    private final Function<Double, CameraTransform> sampler;

    public DirectorRealtimeRenderSessionFinal(DirectorExportSpec spec, Function<Double, CameraTransform> sampler) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.scheduler = new DirectorRealtimeRenderScheduler(spec.fps());
    }

    public DirectorFrame frame(long index) {
        if (index < 0 || index >= spec.frameCount()) throw new IndexOutOfBoundsException("frame: " + index);
        return DirectorExportPlanner.sample(spec, index, sampler);
    }

    public int advance() {
        long before = scheduler.emittedFrames();
        scheduler.advance();
        long after = Math.min(scheduler.emittedFrames(), spec.frameCount());
        return (int) Math.max(0, after - Math.min(before, spec.frameCount()));
    }

    public long emittedFrames() { return Math.min(scheduler.emittedFrames(), spec.frameCount()); }
    public long elapsedTicks() { return scheduler.elapsedTicks(); }
    public boolean complete() { return emittedFrames() >= spec.frameCount(); }
}
