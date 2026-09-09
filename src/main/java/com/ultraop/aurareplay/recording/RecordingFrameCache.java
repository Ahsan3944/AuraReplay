package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Bounded LRU cache for indexed playback. Disk misses can be prefetched off the server thread. */
public final class RecordingFrameCache {
    private final Path path;
    private final int capacity;
    private final LinkedHashMap<Integer, TickSnapshot> frames;

    public RecordingFrameCache(Path path, int capacity) {
        this.path = Objects.requireNonNull(path).toAbsolutePath().normalize();
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
        this.frames = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Integer, TickSnapshot> eldest) {
                return size() > RecordingFrameCache.this.capacity;
            }
        };
    }

    public synchronized TickSnapshot get(int frameIndex) throws IOException {
        TickSnapshot cached = frames.get(frameIndex);
        if (cached != null) return cached;
        TickSnapshot loaded = RecordingIndexedFile.readFrame(path, frameIndex);
        frames.put(frameIndex, loaded);
        return loaded;
    }

    /** Non-blocking cache lookup. Returns null when the frame is not warmed yet. */
    public synchronized TickSnapshot peek(int frameIndex) { return frames.get(frameIndex); }

    /** Loads a bounded neighborhood. Intended for an async executor, never the server tick thread. */
    public synchronized void prefetch(int center, int radius) throws IOException {
        if (radius < 0) throw new IllegalArgumentException("radius must be >= 0");
        if (center < 0) throw new IllegalArgumentException("center must be >= 0");
        int start = Math.max(0, center - radius);
        int end = center + radius;
        if (end < center) end = Integer.MAX_VALUE;
        for (int i = start; i <= end; i++) {
            if (!frames.containsKey(i)) {
                try { frames.put(i, RecordingIndexedFile.readFrame(path, i)); }
                catch (IndexOutOfBoundsException | IOException e) {
                    if (i == end) throw e;
                    break;
                }
            }
            if (i == Integer.MAX_VALUE) break;
        }
    }

    public CompletableFuture<Void> prefetchAsync(int center, int radius, Executor executor) {
        Objects.requireNonNull(executor, "executor");
        return CompletableFuture.runAsync(() -> {
            try { prefetch(center, radius); }
            catch (IOException e) { throw new IllegalStateException("Could not prefetch recording frames", e); }
        }, executor);
    }

    public synchronized void clear() { frames.clear(); }
    public synchronized int size() { return frames.size(); }
    public Path path() { return path; }
}
