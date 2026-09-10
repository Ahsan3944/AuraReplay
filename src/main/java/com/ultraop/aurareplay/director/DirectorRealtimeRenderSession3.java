package com.ultraop.aurareplay.director;

/** Placeholder-free session coordinator: timing is delegated to DirectorRealtimeRenderScheduler. */
public final class DirectorRealtimeRenderSession3 {
    private final DirectorRealtimeRenderScheduler scheduler;
    private final DirectorExportSpec spec;

    public DirectorRealtimeRenderSession3(DirectorExportSpec spec) {
        this.spec = java.util.Objects.requireNonNull(spec, "spec");
        this.scheduler = new DirectorRealtimeRenderScheduler(spec.fps());
    }

    public DirectorExportSpec spec() { return spec; }
    public long emittedFrames() { return Math.min(scheduler.emittedFrames(), spec.frameCount()); }
    public long elapsedTicks() { return scheduler.elapsedTicks(); }

    public int tick() {
        long before = scheduler.emittedFrames();
        scheduler.advance();
        return (int) Math.min(spec.frameCount(), scheduler.emittedFrames()) - (int) Math.min(spec.frameCount(), before);
    }
}
