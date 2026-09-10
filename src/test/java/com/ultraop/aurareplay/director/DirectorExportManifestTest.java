package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DirectorExportManifestTest {
    @Test
    void acceptsCompleteContiguousFramesAndSerializesMetadata() {
        DirectorExportSpec spec = new DirectorExportSpec("scene\"one", 0, 2, 20, 1920, 1080);
        List<DirectorFrame> frames = List.of(
                new DirectorFrame(0, 0.0, new CameraTransform(1, 2, 3, 4, 5, 6, 70)),
                new DirectorFrame(1, 1.0, new CameraTransform(2, 3, 4, 5, 6, 7, 71))
        );

        DirectorExportManifest manifest = new DirectorExportManifest(spec, frames);
        String json = manifest.toJson();

        assertTrue(manifest.complete());
        assertTrue(json.contains("\"scene\": \"scene\\\"one\""));
        assertTrue(json.contains("\"frameCount\": 2"));
        assertTrue(json.contains("\"index\": 0"));
        assertTrue(json.contains("\"x\": 2"));
        assertTrue(json.endsWith("}\n"));
    }

    @Test
    void rejectsMissingOrMisorderedFrames() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 3, 20, 1280, 720);
        DirectorFrame frame0 = new DirectorFrame(0, 0.0, new CameraTransform(0, 0, 0, 0, 0, 0, 70));
        DirectorFrame frame2 = new DirectorFrame(2, 2.0, new CameraTransform(0, 0, 0, 0, 0, 0, 70));

        assertThrows(IllegalArgumentException.class,
                () -> new DirectorExportManifest(spec, List.of(frame0, frame2)));
    }

    @Test
    void rejectsFrameWithWrongTimelineTick() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 10, 20, 40, 1280, 720);
        DirectorFrame wrong = new DirectorFrame(1, 10.75,
                new CameraTransform(0, 0, 0, 0, 0, 0, 70));

        assertThrows(IllegalArgumentException.class,
                () -> new DirectorExportManifest(spec, List.of(
                        new DirectorFrame(0, 10.0, new CameraTransform(0, 0, 0, 0, 0, 0, 70)), wrong)));
    }
}
