package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecordingSeekReaderTest {
    @Test
    void seeksByFrameFromNearestCheckpoint() {
        Recording recording = recording(55);
        RecordingSeekReader reader = new RecordingSeekReader(recording);

        assertEquals(0, reader.current().tick());
        assertEquals(0, reader.index().floorByFrame(19).frameIndex());
        assertEquals(20, reader.index().floorByFrame(20).frameIndex());
        assertEquals(40, reader.index().floorByFrame(54).frameIndex());

        assertEquals(37, reader.seekFrame(37).tick());
        assertEquals(37, reader.frameIndex());
        assertEquals(54, reader.seekFrame(999).tick());
        assertEquals(0, reader.seekFrame(-1).tick());
    }

    @Test
    void seeksByTickAndKeepsLastFrameAtOrBeforeTarget() {
        Recording recording = new Recording("ticks", 50, List.of(
                new TickSnapshot(0, List.of()),
                new TickSnapshot(5, List.of()),
                new TickSnapshot(10, List.of()),
                new TickSnapshot(20, List.of()),
                new TickSnapshot(40, List.of())
        ));
        RecordingSeekReader reader = new RecordingSeekReader(recording);

        assertEquals(10, reader.seekTick(19).tick());
        assertEquals(20, reader.seekTick(20).tick());
        assertEquals(40, reader.seekTick(999).tick());
        assertEquals(0, reader.seekTick(-100).tick());
    }

    @Test
    void stepClampsToRecordingBounds() {
        RecordingSeekReader reader = new RecordingSeekReader(recording(3));

        assertEquals(1, reader.step(1).tick());
        assertEquals(2, reader.step(50).tick());
        assertEquals(0, reader.step(-50).tick());
    }

    @Test
    void emptyRecordingIsSafe() {
        RecordingSeekReader reader = new RecordingSeekReader(new Recording("empty", 0, List.of()));

        assertNull(reader.current());
        assertNull(reader.seekFrame(10));
        assertNull(reader.seekTick(10));
        assertNull(reader.step(1));
        assertEquals(-1, reader.frameIndex());
    }

    private static Recording recording(int count) {
        List<TickSnapshot> frames = java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new TickSnapshot(i, List.of()))
                .toList();
        return new Recording("seek", count, frames);
    }
}
