package com.ultraop.aurareplay.director;

/** Pure timing helper for mapping Minecraft ticks to deterministic export frames. */
public final class DirectorRealtimeRenderScheduler {
    private final int fps;
    private long emittedFrames;
    private long elapsedTicks;

    public DirectorRealtimeRenderScheduler(int fps) {
        if (fps <= 0 || fps > 240) throw new IllegalArgumentException("fps must be between 1 and 240");
        this.fps = fps;
    }

    public int fps() { return fps; }
    public long emittedFrames() { return emittedFrames; }
    public long elapsedTicks() { return elapsedTicks; }

    /** Advances one server tick and returns the number of frames that are now due. */
    public int advance() {
        elapsedTicks++;
        long due = (long) Math.floor(elapsedTicks * (double) fps / 20.0 + 1e-12);
        return (int) Math.max(0L, due - emittedFrames);
    }

    /** Marks successfully rendered frames so a failed bridge never consumes them. */
    public void markEmitted(int count) {
        if (count < 0) throw new IllegalArgumentException("count must be >= 0");
        emittedFrames += count;
    }

    public void reset() {
        emittedFrames = 0L;
        elapsedTicks = 0L;
    }
}
