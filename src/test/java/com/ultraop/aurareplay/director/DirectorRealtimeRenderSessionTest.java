package com.ultraop.aurareplay.director;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectorRealtimeRenderSessionTest {
    @Test
    void requestedFpsProducesExactFrameCountAndTimeline() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 20, 40, 1280, 720);
        assertEquals(40, spec.frameCount());
        assertEquals(0.0, spec.tickForFrame(0), 1e-12);
        assertEquals(0.5, spec.tickForFrame(1), 1e-12);
        assertEquals(19.5, spec.tickForFrame(39), 1e-12);
    }

    @Test
    void sixtyFpsRealtimeScheduleEmitsExactlyThreeFramesPerServerTick() {
        DirectorRealtimeRenderScheduler scheduler = new DirectorRealtimeRenderScheduler(60);
        long total = 0;
        for (int i = 0; i < 20; i++) {
            int due = scheduler.advance();
            assertEquals(3, due);
            scheduler.markEmitted(due);
            total += due;
        }
        assertEquals(60, total);
        assertEquals(60, scheduler.emittedFrames());
    }

    @Test
    void thirtyFpsRealtimeScheduleHasNoDrift() {
        DirectorRealtimeRenderScheduler scheduler = new DirectorRealtimeRenderScheduler(30);
        for (int i = 0; i < 20; i++) {
            int due = scheduler.advance();
            scheduler.markEmitted(due);
        }
        assertEquals(30, scheduler.emittedFrames());
        assertEquals(20, scheduler.elapsedTicks());
    }
}
