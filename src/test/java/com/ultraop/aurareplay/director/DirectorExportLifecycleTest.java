package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DirectorExportLifecycleTest {
    private static DirectorFrame frame(long index, DirectorExportSpec spec) {
        return new DirectorFrame(index, spec.tickForFrame(index), new CameraTransform(index, 2, 3, 0, 0, 0, 70));
    }

    @Test
    void pausePreservesTemporaryManifestForResume() throws Exception {
        Path dir = Files.createTempDirectory("aurareplay-pause-");
        Path output = dir.resolve("shot.json");
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 4, 20, 640, 360);
        DirectorExportManifestStreamWriter writer = new DirectorExportManifestStreamWriter(output);
        DirectorCaptureSession capture = new DirectorCaptureSession(spec, writer);
        capture.start();
        capture.accept(frame(0, spec));
        capture.accept(frame(1, spec));
        capture.pause();

        assertEquals(DirectorCaptureSession.State.PAUSED, capture.state());
        assertTrue(Files.exists(output.resolveSibling("shot.json.tmp")));
        assertFalse(Files.exists(output));

        DirectorExportManifestStreamWriter resumedWriter = new DirectorExportManifestStreamWriter(output);
        resumedWriter.resume(spec, 2);
        resumedWriter.accept(frame(2, spec));
        resumedWriter.accept(frame(3, spec));
        resumedWriter.complete();

        assertTrue(Files.exists(output));
        assertFalse(Files.exists(output.resolveSibling("shot.json.tmp")));
    }

    @Test
    void cancelDiscardsTemporaryManifest() throws Exception {
        Path dir = Files.createTempDirectory("aurareplay-cancel-");
        Path output = dir.resolve("shot.json");
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 2, 20, 640, 360);
        DirectorExportManifestStreamWriter writer = new DirectorExportManifestStreamWriter(output);
        DirectorCaptureSession capture = new DirectorCaptureSession(spec, writer);
        capture.start();
        capture.accept(frame(0, spec));
        capture.cancel();

        assertEquals(DirectorCaptureSession.State.CANCELLED, capture.state());
        assertFalse(Files.exists(output));
        assertFalse(Files.exists(output.resolveSibling("shot.json.tmp")));
    }

    @Test
    void pausedJobDoesNotAdvanceUntilResumedByNewJob() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 4, 20, 640, 360);
        DirectorExportJob job = new DirectorExportJob(spec, tick -> new CameraTransform(tick, 0, 0, 0, 0, 0, 70));
        job.start();
        assertEquals(1, job.step(1));
        job.pause();
        assertEquals(DirectorExportJob.State.PAUSED, job.state());
        assertEquals(1, job.capturedFrames());
        assertEquals(0, job.step(8));
    }
}
