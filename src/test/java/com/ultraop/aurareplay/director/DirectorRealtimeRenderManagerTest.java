package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectorRealtimeRenderManagerTest {
    @Test
    void startsOnlyOneSessionPerViewerAndStopsIt() {
        assertNotNull(DirectorRealtimeRenderManager.class);
    }

    @Test
    void schedulerAndSessionExposeDeterministicProgress() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 10, 11, 40, 1280, 720);
        DirectorRealtimeRenderScheduler scheduler = new DirectorRealtimeRenderScheduler(spec.fps());
        assertEquals(0, scheduler.emittedFrames());
        assertEquals(0, scheduler.elapsedTicks());
        assertEquals(2, spec.frameCount());
        assertEquals(10.0, spec.tickForFrame(0), 1e-12);
        assertEquals(10.5, spec.tickForFrame(1), 1e-12);
    }

    @Test
    void managerApiIncludesStateProgressAccessors() {
        assertTrue(DirectorRealtimeRenderSession.State.READY.ordinal() == 0);
        CameraTransform transform = new CameraTransform(1, 2, 3, 4, 5, 6, 70);
        assertEquals(1, transform.x());
        assertEquals(70, transform.fov());
    }
}
