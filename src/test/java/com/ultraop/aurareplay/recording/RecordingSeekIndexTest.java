package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecordingSeekIndexTest {
    @Test
    void buildsCheckpointsAtCodecKeyframeBoundaries() {
        List<TickSnapshot> frames = new ArrayList<>();
        for (int i = 0; i < 45; i++) frames.add(new TickSnapshot(i * 2L, List.of(), List.of()));

        RecordingSeekIndex index = RecordingSeekIndex.build(new Recording("seek", 90, frames));

        assertEquals(List.of(
                new RecordingSeekIndex.Checkpoint(0, 0),
                new RecordingSeekIndex.Checkpoint(20, 40),
                new RecordingSeekIndex.Checkpoint(40, 80)
        ), index.checkpoints());
    }

    @Test
    void resolvesNearestCheckpointByFrameAndTick() {
        List<TickSnapshot> frames = new ArrayList<>();
        for (int i = 0; i < 45; i++) frames.add(new TickSnapshot(i * 2L, List.of(), List.of()));
        RecordingSeekIndex index = RecordingSeekIndex.build(new Recording("seek", 90, frames));

        assertEquals(20, index.floorByFrame(39).frameIndex());
        assertEquals(40, index.floorByTick(80).frameIndex());
        assertEquals(20, index.floorByTick(79).frameIndex());
        assertEquals(0, index.floorByTick(-1).frameIndex());
    }

    @Test
    void emptyRecordingHasNoCheckpoints() {
        RecordingSeekIndex index = RecordingSeekIndex.build(new Recording("empty", 0, List.of()));
        assertTrue(index.checkpoints().isEmpty());
        assertThrows(IllegalStateException.class, () -> index.floorByFrame(0));
    }
}
