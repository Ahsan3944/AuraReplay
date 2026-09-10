package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DirectorFrameStreamWriterTest {
    @Test
    void streamsFramesAndAtomicallyFinalizesOnComplete() throws Exception {
        Path dir = Files.createTempDirectory("aurareplay-stream-");
        Path output = dir.resolve("take.jsonl");
        DirectorExportSpec spec = new DirectorExportSpec("scene\"one", 10, 11, 40, 1280, 720);
        DirectorFrameStreamWriter sink = new DirectorFrameStreamWriter(output);
        sink.start(spec);
        sink.accept(frame(spec, 0));
        sink.accept(frame(spec, 1));
        assertEquals(2, sink.framesWritten());
        sink.complete();

        assertTrue(Files.exists(output));
        assertFalse(Files.exists(dir.resolve("take.jsonl.tmp")));
        List<String> lines = Files.readAllLines(output);
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("director-stream"));
        assertTrue(lines.get(0).contains("scene\\\"one"));
        assertTrue(lines.get(1).contains("\"index\":0"));
        assertTrue(lines.get(2).contains("\"index\":1"));
    }

    @Test
    void cancelRemovesTemporaryStreamAndDoesNotPublishPartialOutput() throws Exception {
        Path dir = Files.createTempDirectory("aurareplay-stream-");
        Path output = dir.resolve("take.jsonl");
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 1, 20, 1280, 720);
        DirectorFrameStreamWriter sink = new DirectorFrameStreamWriter(output);
        sink.start(spec);
        sink.accept(frame(spec, 0));
        sink.cancel();

        assertFalse(Files.exists(output));
        assertFalse(Files.exists(dir.resolve("take.jsonl.tmp")));
        assertFalse(sink.open());
    }

    @Test
    void rejectsOutOfOrderFrames() throws Exception {
        Path dir = Files.createTempDirectory("aurareplay-stream-");
        Path output = dir.resolve("take.jsonl");
        DirectorExportSpec spec = new DirectorExportSpec("scene", 10, 11, 40, 1280, 720);
        DirectorFrameStreamWriter sink = new DirectorFrameStreamWriter(output);
        sink.start(spec);
        assertThrows(IllegalArgumentException.class, () -> sink.accept(frame(spec, 1)));
        sink.cancel();
    }

    private static DirectorFrame frame(DirectorExportSpec spec, long index) {
        return new DirectorFrame(index, spec.tickForFrame(index),
                new CameraTransform(index, 64, -index, 10, 20, 0, 70));
    }
}
