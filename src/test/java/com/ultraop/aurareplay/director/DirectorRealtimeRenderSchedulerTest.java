package com.ultraop.aurareplay.director;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectorRealtimeRenderSchedulerTest {
    @Test
    void thirtyFpsEmitsOneOrTwoFramesPerServerTickWithoutDrift() {
        DirectorRealtimeRenderScheduler scheduler = new DirectorRealtimeRenderScheduler(30);
        int[] expected = {1, 1, 2, 1, 2, 1, 2, 1, 2, 1};
        for (int value : expected) assertEquals(value, scheduler.advance());
        assertEquals(15, scheduler.emittedFrames());
        assertEquals(10, scheduler.elapsedTicks());
    }

    @Test
    void sixtyFpsEmitsThreeFramesPerServerTick() {
        DirectorRealtimeRenderScheduler scheduler = new DirectorRealtimeRenderScheduler(60);
        for (int i = 0; i < 10; i++) assertEquals(3, scheduler.advance());
        assertEquals(30, scheduler.emittedFrames());
    }

    @Test
    void resetStartsTimingAgain() {
        DirectorRealtimeRenderScheduler scheduler = new DirectorRealtimeRenderScheduler(24);
        scheduler.advance();
        scheduler.reset();
        assertEquals(0, scheduler.emittedFrames());
        assertEquals(0, scheduler.elapsedTicks());
        assertEquals(1, scheduler.advance());
    }
}
