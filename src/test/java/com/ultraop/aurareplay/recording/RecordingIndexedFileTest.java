package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.recording.snapshot.EntitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RecordingIndexedFileTest {
    @Test
    void writesAndReadsCompleteRecording() throws Exception {
        Recording original = recording(45);
        Path path = Files.createTempFile("aurareplay-", ".arrec");
        try {
            RecordingIndexedFile.write(path, original);
            Recording decoded = RecordingIndexedFile.read(path);
            assertEquals(original, decoded);
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void readsFrameFromOnlyItsIndexedBlock() throws Exception {
        Recording original = recording(45);
        Path path = Files.createTempFile("aurareplay-", ".arrec");
        try {
            RecordingIndexedFile.write(path, original);
            assertEquals(original.frames().get(0), RecordingIndexedFile.readFrame(path, 0));
            assertEquals(original.frames().get(19), RecordingIndexedFile.readFrame(path, 19));
            assertEquals(original.frames().get(20), RecordingIndexedFile.readFrame(path, 20));
            assertEquals(original.frames().get(44), RecordingIndexedFile.readFrame(path, 44));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void readsNearestFrameAtOrBeforeTick() throws Exception {
        Recording original = recording(45);
        Path path = Files.createTempFile("aurareplay-", ".arrec");
        try {
            RecordingIndexedFile.write(path, original);
            assertEquals(original.frames().get(10), RecordingIndexedFile.readTick(path, 10));
            assertEquals(original.frames().get(10), RecordingIndexedFile.readTick(path, 15));
            assertEquals(original.frames().get(20), RecordingIndexedFile.readTick(path, 20));
            assertEquals(original.frames().get(44), RecordingIndexedFile.readTick(path, 999));
            assertEquals(original.frames().get(0), RecordingIndexedFile.readTick(path, -999));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void rejectsTruncatedIndexedFile() throws Exception {
        Recording original = recording(45);
        Path source = Files.createTempFile("aurareplay-source-", ".arrec");
        Path truncated = Files.createTempFile("aurareplay-truncated-", ".arrec");
        try {
            RecordingIndexedFile.write(source, original);
            byte[] bytes = Files.readAllBytes(source);
            Files.write(truncated, java.util.Arrays.copyOf(bytes, bytes.length - 1));
            assertThrows(Exception.class, () -> RecordingIndexedFile.readFrame(truncated, 20));
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(truncated);
        }
    }

    @Test
    void rejectsInvalidFrameIndex() throws Exception {
        Recording original = recording(2);
        Path path = Files.createTempFile("aurareplay-", ".arrec");
        try {
            RecordingIndexedFile.write(path, original);
            assertThrows(IndexOutOfBoundsException.class, () -> RecordingIndexedFile.readFrame(path, 2));
            assertThrows(IllegalArgumentException.class, () -> RecordingIndexedFile.readFrame(path, -1));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    private static Recording recording(int count) {
        UUID uuid = UUID.randomUUID();
        List<TickSnapshot> frames = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            EntitySnapshot entity = new EntitySnapshot(1, uuid, EntityType.ZOMBIE, i, 64, 2, i, 0, 0.1, 0, 0, i % 4, null, null, true);
            frames.add(new TickSnapshot(i, List.of(entity), List.of()));
        }
        return new Recording("IndexedTake", count, frames);
    }
}
