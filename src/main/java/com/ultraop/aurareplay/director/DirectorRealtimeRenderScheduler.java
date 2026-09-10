package com.ultraop.aurareplay.director;

import java.util.Objects;

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

    /** Advances one server tick and returns the number of newly due frames. */
    public int advance() {
        elapsedTicks++;
        long due = (long) Math.floor(elapsedTicks * (double) fps / 20.0 + 1e-12);
        int count = (int) Math.max(0L, due - emittedFrames);
        emittedFrames += count;
        return count;
    }

    public void reset() {
        emittedFrames = 0L;
        elapsedTicks = 0L;
    }
}
