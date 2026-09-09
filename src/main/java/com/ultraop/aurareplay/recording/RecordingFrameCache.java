package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Small LRU frame cache for indexed playback. The cache is scoped to one recording path. */
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

    /** Loads a bounded neighborhood around the requested frame for smooth sequential playback. */
    public synchronized void prefetch(int center, int radius) throws IOException {
        if (radius < 0) throw new IllegalArgumentException("radius must be >= 0");
        int start = Math.max(0, center - radius);
        int end = center + radius;
        for (int i = start; i <= end; i++) {
            if (!frames.containsKey(i)) frames.put(i, RecordingIndexedFile.readFrame(path, i));
        }
    }

    public synchronized void clear() { frames.clear(); }
    public synchronized int size() { return frames.size(); }
    public Path path() { return path; }
}
