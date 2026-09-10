package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DirectorExportResumeTest {
    private static DirectorFrame frame(long index, double tick) {
        return new DirectorFrame(index, tick, new CameraTransform(tick, 2, 3, 0, 0, 0, 70));
    }

    @Test
    void resumesAtCheckpointWithoutDuplicatingFrame() throws Exception {
        Path dir = Files.createTempDirectory("aurareplay-resume");
        Path output = dir.resolve("export.json");
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 4, 20, 640, 360);
        DirectorExportManifestStreamWriter first = new DirectorExportManifestStreamWriter(output);
        first.start(spec);
        first.accept(frame(0, 0)); first.accept(frame(1, 1)); first.accept(frame(2, 2));
        DirectorExportManifestStreamWriter resumed = new DirectorExportManifestStreamWriter(output);
        resumed.resume(spec, 2);
        resumed.accept(frame(2, 2)); resumed.accept(frame(3, 3)); resumed.complete();
        String json = Files.readString(output);
        assertEquals(1, occurrences(json, "\"index\": 2,"));
        assertEquals(1, occurrences(json, "\"index\": 3,"));
        assertFalse(Files.exists(output.resolveSibling("export.json.tmp")));
    }

    @Test
    void resumableJobContinuesFromCheckpointFrame() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 10, 11, 40, 640, 360);
        DirectorExportJob job = new DirectorExportJob(spec, tick ->
                new CameraTransform(tick.floatValue(), 0, 0, 0, 0, 0, 70), null, false);
        job.startAt(1);
        assertEquals(1, job.nextFrameIndex());
        assertEquals(1, job.capturedFrames());
        assertEquals(1, job.step(1));
        assertEquals(DirectorExportJob.State.COMPLETED, job.state());
    }

    private static int occurrences(String value, String needle) {
        int count = 0, at = 0;
        while ((at = value.indexOf(needle, at)) >= 0) { count++; at += needle.length(); }
        return count;
    }
}
