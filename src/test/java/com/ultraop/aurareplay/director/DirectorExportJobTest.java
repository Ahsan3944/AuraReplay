package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectorExportJobTest {
    private static DirectorExportSpec spec() {
        return new DirectorExportSpec("job", 10, 12, 20, 1280, 720);
    }

    @Test
    void capturesIncrementallyWithoutDuplicateFrames() {
        DirectorExportJob job = new DirectorExportJob(spec(), tick ->
                new CameraTransform(tick, 0, 0, tick, 0, 0, 70));

        assertEquals(1, job.step(1));
        assertEquals(1, job.capturedFrames());
        assertEquals(0, job.frames().get(0).frameIndex());
        assertEquals(10.0, job.frames().get(0).sceneTick());

        assertEquals(1, job.step(1));
        assertEquals(2, job.capturedFrames());
        assertEquals(1, job.frames().get(1).frameIndex());
        assertEquals(11.0, job.frames().get(1).sceneTick());
        assertEquals(DirectorExportJob.State.COMPLETED, job.state());
    }

    @Test
    void respectsPerStepBudgetAndCanFinishLater() {
        DirectorExportSpec spec = new DirectorExportSpec("job", 0, 5, 40, 640, 360);
        DirectorExportJob job = new DirectorExportJob(spec, tick ->
                new CameraTransform(tick, 0, 0, 0, 0, 0, 70));

        assertEquals(2, job.step(2));
        assertEquals(2, job.capturedFrames());
        assertEquals(2, job.nextFrameIndex());
        assertEquals(2, job.step(2));
        assertEquals(4, job.capturedFrames());
        assertEquals(1, job.step(2));
        assertEquals(5, job.capturedFrames());
        assertEquals(DirectorExportJob.State.COMPLETED, job.state());
        assertEquals(0, job.step(2));
    }

    @Test
    void samplerFailureStopsJobWithoutAdvancingFailedFrame() {
        DirectorExportJob job = new DirectorExportJob(spec(), tick -> {
            if (tick >= 11.0) throw new IllegalStateException("sample failed");
            return new CameraTransform(0, 0, 0, 0, 0, 0, 70);
        });

        assertEquals(1, job.step(4));
        assertEquals(1, job.capturedFrames());
        assertEquals(DirectorExportJob.State.FAILED, job.state());
        assertNotNull(job.failure());
        assertEquals(1, job.nextFrameIndex());
    }

    @Test
    void cancelPreventsFurtherSampling() {
        DirectorExportJob job = new DirectorExportJob(spec(), tick ->
                new CameraTransform(0, 0, 0, 0, 0, 0, 70));
        job.cancel();
        assertEquals(DirectorExportJob.State.CANCELLED, job.state());
        assertEquals(0, job.step(4));
        assertEquals(0, job.capturedFrames());
    }
}
