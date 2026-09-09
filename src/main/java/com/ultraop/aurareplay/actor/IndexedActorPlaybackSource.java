package com.ultraop.aurareplay.actor;

import com.ultraop.aurareplay.recording.RecordingIndexedFile;
import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Disk-backed actor source. Only the indexed block containing a requested frame is decoded. */
public final class IndexedActorPlaybackSource implements ActorPlaybackSource {
    private final Path path;
    private final int frameCount;

    public IndexedActorPlaybackSource(Path path, int frameCount) {
        this.path = Objects.requireNonNull(path, "path");
        if (frameCount < 0) throw new IllegalArgumentException("frameCount must be >= 0");
        this.frameCount = frameCount;
    }

    public Path path() { return path; }
    @Override public int frameCount() { return frameCount; }
    @Override public TickSnapshot frame(int index) throws IOException {
        if (index < 0 || index >= frameCount) throw new IndexOutOfBoundsException("frameIndex=" + index);
        return RecordingIndexedFile.readFrame(path, index);
    }
}
