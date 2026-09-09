package com.ultraop.aurareplay.actor;

import com.ultraop.aurareplay.recording.RecordingFrameCache;
import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Disk-backed actor source using an indexed recording frame cache. */
public final class IndexedActorPlaybackSource implements ActorPlaybackSource {
    public static final int DEFAULT_CACHE_CAPACITY = 64;
    public static final int DEFAULT_PREFETCH_RADIUS = 8;

    private final Path path;
    private final int frameCount;
    private final RecordingFrameCache cache;

    public IndexedActorPlaybackSource(Path path, int frameCount) { this(path, frameCount, DEFAULT_CACHE_CAPACITY); }

    public IndexedActorPlaybackSource(Path path, int frameCount, int cacheCapacity) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (frameCount < 0) throw new IllegalArgumentException("frameCount must be >= 0");
        this.frameCount = frameCount;
        this.cache = new RecordingFrameCache(this.path, cacheCapacity);
    }

    public Path path() { return path; }
    @Override public int frameCount() { return frameCount; }

    @Override public TickSnapshot frame(int index) throws IOException {
        if (index < 0 || index >= frameCount) throw new IndexOutOfBoundsException("frameIndex=" + index);
        return cache.get(index);
    }

    /** Main-thread-safe lookup: returns only already warmed frames and never touches disk. */
    public TickSnapshot peek(int index) {
        if (index < 0 || index >= frameCount) return null;
        return cache.peek(index);
    }

    public void prefetch(int center, int radius) throws IOException {
        if (frameCount == 0) return;
        cache.prefetch(Math.max(0, Math.min(center, frameCount - 1)), Math.max(0, radius));
    }

    public CompletableFuture<Void> prefetchAsync(int center, int radius, Executor executor) {
        if (frameCount == 0) return CompletableFuture.completedFuture(null);
        int bounded = Math.max(0, Math.min(center, frameCount - 1));
        return cache.prefetchAsync(bounded, Math.max(0, radius), executor);
    }

    public int cachedFrameCount() { return cache.size(); }
    public void clearCache() { cache.clear(); }
}
