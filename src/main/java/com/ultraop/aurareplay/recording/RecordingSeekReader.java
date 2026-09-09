package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;

import java.util.Objects;

/**
 * Fast timeline reader for an immutable recording. Seek starts at the nearest
 * codec keyframe checkpoint and scans only the bounded interval to the target.
 */
public final class RecordingSeekReader {
    private final Recording recording;
    private final RecordingSeekIndex index;
    private int frameIndex;

    public RecordingSeekReader(Recording recording) {
        this.recording = Objects.requireNonNull(recording, "recording");
        this.index = RecordingSeekIndex.build(recording);
        this.frameIndex = recording.frames().isEmpty() ? -1 : 0;
    }

    public Recording recording() {
        return recording;
    }

    public RecordingSeekIndex index() {
        return index;
    }

    public int frameIndex() {
        return frameIndex;
    }

    public TickSnapshot current() {
        if (frameIndex < 0) return null;
        return recording.frames().get(frameIndex);
    }

    public TickSnapshot seekFrame(int targetFrame) {
        if (recording.frames().isEmpty()) {
            frameIndex = -1;
            return null;
        }
        if (targetFrame < 0) targetFrame = 0;
        if (targetFrame >= recording.frames().size()) targetFrame = recording.frames().size() - 1;

        RecordingSeekIndex.Checkpoint checkpoint = index.floorByFrame(targetFrame);
        frameIndex = checkpoint.frameIndex();
        while (frameIndex < targetFrame) frameIndex++;
        return current();
    }

    public TickSnapshot seekTick(long targetTick) {
        if (recording.frames().isEmpty()) {
            frameIndex = -1;
            return null;
        }

        RecordingSeekIndex.Checkpoint checkpoint = index.floorByTick(targetTick);
        frameIndex = checkpoint.frameIndex();
        while (frameIndex + 1 < recording.frames().size()
                && recording.frames().get(frameIndex + 1).tick() <= targetTick) {
            frameIndex++;
        }
        return current();
    }

    public TickSnapshot step(int deltaFrames) {
        if (recording.frames().isEmpty()) {
            frameIndex = -1;
            return null;
        }
        return seekFrame(frameIndex + deltaFrames);
    }
}
