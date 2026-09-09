package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Main-thread-friendly playback cursor backed by an indexed recording on disk. */
public final class RecordingPlaybackSession {
    private final Path path;
    private final int frameCount;
    private int frameIndex;
    private TickSnapshot current;

    public RecordingPlaybackSession(Path path) throws IOException {
        this.path = Objects.requireNonNull(path, "path");
        RecordingMetadata metadata = inspect(path);
        this.frameCount = metadata.frameCount();
        if (frameCount > 0) this.current = RecordingIndexedFile.readFrame(path, 0);
    }

    public Path path() { return path; }
    public int frameIndex() { return frameIndex; }
    public int frameCount() { return frameCount; }
    public TickSnapshot current() { return current; }

    public boolean seekFrame(int target) throws IOException {
        if (frameCount == 0) return false;
        int clamped = Math.max(0, Math.min(target, frameCount - 1));
        if (clamped == frameIndex && current != null) return true;
        current = RecordingIndexedFile.readFrame(path, clamped);
        frameIndex = clamped;
        return true;
    }

    public boolean seekTick(long tick) throws IOException {
        if (frameCount == 0) return false;
        current = RecordingIndexedFile.readTick(path, tick);
        frameIndex = findFrameIndex(current.tick());
        return true;
    }

    public boolean step(int delta) throws IOException {
        return seekFrame(frameIndex + delta);
    }

    private int findFrameIndex(long tick) throws IOException {
        // Blocks are small; resolve the exact index inside the selected block without
        // loading unrelated blocks. This remains bounded by the index block size.
        int estimate = Math.max(0, Math.min(frameCount - 1, (int) tick));
        TickSnapshot candidate = RecordingIndexedFile.readFrame(path, estimate);
        if (candidate.tick() == tick) return estimate;
        if (candidate.tick() < tick) {
            for (int i = estimate + 1; i < Math.min(frameCount, estimate + 21); i++) {
                TickSnapshot frame = RecordingIndexedFile.readFrame(path, i);
                if (frame.tick() >= tick) return frame.tick() == tick ? i : i - 1;
            }
        } else {
            for (int i = estimate - 1; i >= Math.max(0, estimate - 21); i--) {
                TickSnapshot frame = RecordingIndexedFile.readFrame(path, i);
                if (frame.tick() <= tick) return i;
            }
        }
        return estimate;
    }

    private static RecordingMetadata inspect(Path path) throws IOException {
        Recording recording = RecordingIndexedFile.read(path);
        return new RecordingMetadata(recording.name(), recording.frames().size());
    }

    private record RecordingMetadata(String name, int frameCount) { }
}
