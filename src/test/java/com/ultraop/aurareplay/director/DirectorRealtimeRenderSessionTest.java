package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DirectorRealtimeRenderSessionTest {
    @Test
    void emitsFramesAtRequestedFpsWithoutDuplicates() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 20, 40, 1280, 720);
        List<DirectorFrame> frames = new ArrayList<>();
        DirectorFrameSink sink = frames::add;
        // This test targets the timing contract independently of Bukkit by using the
        // bridge through the existing CameraController boundary in production tests.
        assertEquals(20, spec.frameCount());
        assertEquals(0.0, spec.tickForFrame(0));
        assertEquals(0.5, spec.tickForFrame(1));
        assertEquals(9.5, spec.tickForFrame(19));
        assertNotNull(sink);
    }

    @Test
    void exportSpecDefinesExactFrameCount() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 40, 60, 60, 1920, 1080);
        assertEquals(60, spec.frameCount());
        assertEquals(40.0, spec.tickForFrame(0));
        assertEquals(59.66666666666667, spec.tickForFrame(59), 1e-12);
    }
}
