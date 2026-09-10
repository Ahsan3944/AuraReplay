package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DirectorExportManifestStreamWriterTest {
    private static DirectorExportSpec spec() {
        return new DirectorExportSpec("stream\"scene", 10, 11, 40, 1280, 720);
    }

    private static DirectorFrame frame(DirectorExportSpec spec, long index) {
        double tick = spec.tickForFrame(index);
        return new DirectorFrame(index, tick,
                new CameraTransform(tick, 2, 3, 4, 5, 6, 70));
    }

    @Test
    void writesManifestIncrementallyWithExpectedSchema() throws Exception {
        Path dir = Files.createTempDirectory("aurareplay-stream-");
        Path output = dir.resolve("scene.json");
        DirectorExportManifestStreamWriter writer = new DirectorExportManifestStreamWriter(output);
        writer.start(spec());
        writer.accept(frame(spec(), 0));
        writer.accept(frame(spec(), 1));
        writer.complete();

        assertTrue(Files.exists(output));
        assertFalse(Files.exists(output.resolveSibling("scene.json.tmp")));
        String json = Files.readString(output);
        assertTrue(json.contains("\"scene\": \"stream\\\"scene\""));
        assertTrue(json.contains("\"frameCount\": 2"));
        assertTrue(json.contains("\"index\": 0"));
        assertTrue(json.contains("\"index\": 1"));
        assertTrue(json.trim().endsWith("}"));
    }

    @Test
    void rejectsWrongTickAndLeavesOnlyTemporaryOutput() throws Exception {
        Path dir = Files.createTempDirectory("aurareplay-stream-");
        Path output = dir.resolve("scene.json");
        DirectorExportSpec spec = spec();
        DirectorExportManifestStreamWriter writer = new DirectorExportManifestStreamWriter(output);
        writer.start(spec);
        DirectorFrame bad = new DirectorFrame(0, 999,
                new CameraTransform(0, 0, 0, 0, 0, 0, 70));

        assertThrows(IllegalArgumentException.class, () -> writer.accept(bad));
        writer.cancel();
        assertFalse(Files.exists(output));
        assertFalse(Files.exists(output.resolveSibling("scene.json.tmp")));
    }

    @Test
    void cancellationDoesNotPublishPartialManifest() throws Exception {
        Path dir = Files.createTempDirectory("aurareplay-stream-");
        Path output = dir.resolve("scene.json");
        DirectorExportManifestStreamWriter writer = new DirectorExportManifestStreamWriter(output);
        writer.start(spec());
        writer.accept(frame(spec(), 0));
        writer.cancel();

        assertFalse(Files.exists(output));
        assertFalse(Files.exists(output.resolveSibling("scene.json.tmp")));
    }
}
