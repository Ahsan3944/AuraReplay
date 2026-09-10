package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectorExportSpecTest {
    @Test
    void mapsFramesToTwentyTickSecondTimeline() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 40, 60, 30, 1920, 1080);
        assertEquals(30, spec.frameCount());
        assertEquals(40.0, spec.tickForFrame(0), 1e-9);
        assertEquals(40.0 + 20.0 / 30.0, spec.tickForFrame(1), 1e-9);
        assertEquals(59.333333333333336, spec.tickForFrame(29), 1e-9);
    }

    @Test
    void rejectsInvalidExportRangesAndFrames() {
        assertThrows(IllegalArgumentException.class, () -> new DirectorExportSpec("scene", 10, 10, 30, 1920, 1080));
        assertThrows(IllegalArgumentException.class, () -> new DirectorExportSpec("scene", 0, 20, 0, 1920, 1080));
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 20, 24, 1280, 720);
        assertThrows(IndexOutOfBoundsException.class, () -> spec.tickForFrame(spec.frameCount()));
    }

    @Test
    void plannerDelegatesSamplingAtExactFrameTick() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 20, 20, 1280, 720);
        DirectorFrame frame = DirectorExportPlanner.sample(spec, 7,
                tick -> new CameraTransform(tick, 64, 0, 0, 0, 0, 70));
        assertEquals(7, frame.frameIndex());
        assertEquals(7.0, frame.sceneTick(), 1e-9);
        assertEquals(7.0, frame.camera().x(), 1e-9);
    }
}
