package com.ultraop.aurareplay.director;

import java.util.Objects;

/** Immutable, validated settings describing a deterministic director export. */
public record DirectorExportSpec(
        String sceneName,
        long startTick,
        long endTick,
        int fps,
        int width,
        int height
) {
    public DirectorExportSpec {
        sceneName = Objects.requireNonNull(sceneName, "sceneName").trim();
        if (sceneName.isEmpty()) throw new IllegalArgumentException("sceneName cannot be empty");
        if (startTick < 0) throw new IllegalArgumentException("startTick must be >= 0");
        if (endTick <= startTick) throw new IllegalArgumentException("endTick must be > startTick");
        if (fps <= 0 || fps > 240) throw new IllegalArgumentException("fps must be between 1 and 240");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("resolution must be positive");
    }

    public long frameCount() {
        return Math.max(0L, Math.round((endTick - startTick) * (double) fps / 20.0));
    }

    public double tickForFrame(long frame) {
        if (frame < 0 || frame >= frameCount()) throw new IndexOutOfBoundsException("frame: " + frame);
        return startTick + frame * 20.0 / fps;
    }
}
