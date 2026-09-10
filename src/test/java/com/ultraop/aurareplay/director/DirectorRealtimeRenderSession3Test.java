package com.ultraop.aurareplay.director;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectorRealtimeRenderSession3Test {
    @Test
    void capsReportedFramesAtExportFrameCount() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 1, 240, 1280, 720);
        DirectorRealtimeRenderSession3 session = new DirectorRealtimeRenderSession3(spec);
        int emitted = 0;
        for (int i = 0; i < 20; i++) emitted += session.tick();
        assertEquals(spec.frameCount(), session.emittedFrames());
        assertEquals(spec.frameCount(), emitted);
    }
}
