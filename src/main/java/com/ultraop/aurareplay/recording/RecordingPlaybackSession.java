package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Main-thread-friendly playback cursor backed by an indexed recording and bounded frame cache. */
public final class RecordingPlaybackSession {
    private static final int DEFAULT_CACHE_CAPACITY = 64;
    private static final int PREFETCH_RADIUS = 8;

    private final Path path;
    private final int frameCount;
    private final RecordingFrameCache cache;
    private int frameIndex;
    private TickSnapshot current;

    public RecordingPlaybackSession(Path path) throws IOException {
        this(path, DEFAULT_CACHE_CAPACITY);
    }

    public RecordingPlaybackSession(Path path, int cacheCapacity) throws IOException {
        this.path = Objects.requireNonNull(path).toAbsolutePath().normalize();
        Recording recording = RecordingIndexedFile.read(this.path);
        this.frameCount = recording.frames().size();
        this.cache = new RecordingFrameCache(this.path, cacheCapacity);
        if (frameCount > 0) {
            this.current = cache.get(0);
            cache.prefetch(0, PREFETCH_RADIUS);
        }
    }

    public Path path() { return path; }
    public int frameIndex() { return frameIndex; }
    public int frameCount() { return frameCount; }
    public TickSnapshot current() { return current; }
    public int cachedFrameCount() { return cache.size(); }

    public boolean seekFrame(int target) throws IOException {
        if (frameCount == 0) return false;
        int clamped = Math.max(0, Math.min(target, frameCount - 1));
        if (clamped == frameIndex && current != null) return true;
        current = cache.get(clamped);
        frameIndex = clamped;
        cache.prefetch(clamped, PREFETCH_RADIUS);
        return true;
    }

    public boolean seekTick(long tick) throws IOException {
        if (frameCount == 0) return false;
        TickSnapshot selected = RecordingIndexedFile.readTick(path, tick);
        frameIndex = findFrameIndex(selected.tick());
        current = cache.get(frameIndex);
        cache.prefetch(frameIndex, PREFETCH_RADIUS);
        return true;
    }

    public boolean step(int delta) throws IOException {
        return seekFrame(frameIndex + delta);
    }

    public void clearCache() { cache.clear(); }

    private int findFrameIndex(long tick) throws IOException {
        int estimate = Math.max(0, Math.min(frameCount - 1, (int) Math.max(0, Math.min(Integer.MAX_VALUE, tick))));
        TickSnapshot candidate = cache.get(estimate);
        if (candidate.tick() == tick) return estimate;
        if (candidate.tick() < tick) {
            for (int i = estimate + 1; i < Math.min(frameCount, estimate + 21); i++) {
                TickSnapshot frame = cache.get(i);
                if (frame.tick() >= tick) return frame.tick() == tick ? i : i - 1;
            }
        } else {
            for (int i = estimate - 1; i >= Math.max(0, estimate - 21); i--) {
                TickSnapshot frame = cache.get(i);
                if (frame.tick() <= tick) return i;
            }
        }
        return estimate;
    }
}
