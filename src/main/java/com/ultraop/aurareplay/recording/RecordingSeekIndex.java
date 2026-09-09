package com.ultraop.aurareplay.recording;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable seek checkpoints for a recording. A checkpoint is always a codec
 * keyframe boundary, allowing a future reader to resume reconstruction from
 * the nearest safe state instead of replaying the complete timeline.
 */
public final class RecordingSeekIndex {
    private final List<Checkpoint> checkpoints;

    private RecordingSeekIndex(List<Checkpoint> checkpoints) {
        this.checkpoints = List.copyOf(checkpoints);
    }

    public static RecordingSeekIndex build(Recording recording) {
        Objects.requireNonNull(recording, "recording");
        List<Checkpoint> result = new ArrayList<>();
        for (int frame = 0; frame < recording.frames().size(); frame++) {
            if (frame == 0 || frame % RecordingBinaryCodec.KEYFRAME_INTERVAL_FOR_INDEX == 0) {
                result.add(new Checkpoint(frame, recording.frames().get(frame).tick()));
            }
        }
        return new RecordingSeekIndex(result);
    }

    public List<Checkpoint> checkpoints() {
        return checkpoints;
    }

    public Checkpoint floorByFrame(int frame) {
        if (frame < 0) throw new IllegalArgumentException("frame must be >= 0");
        Checkpoint result = null;
        for (Checkpoint checkpoint : checkpoints) {
            if (checkpoint.frameIndex() > frame) break;
            result = checkpoint;
        }
        if (result == null) throw new IllegalStateException("seek index has no checkpoint");
        return result;
    }

    public Checkpoint floorByTick(long tick) {
        Checkpoint result = null;
        for (Checkpoint checkpoint : checkpoints) {
            if (checkpoint.tick() > tick) break;
            result = checkpoint;
        }
        if (result == null) return checkpoints.getFirst();
        return result;
    }

    public record Checkpoint(int frameIndex, long tick) {
        public Checkpoint {
            if (frameIndex < 0) throw new IllegalArgumentException("frameIndex must be >= 0");
        }
    }
}
