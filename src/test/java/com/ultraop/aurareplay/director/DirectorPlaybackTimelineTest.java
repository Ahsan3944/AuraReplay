package com.ultraop.aurareplay.director;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectorPlaybackTimelineTest {
    private static DirectorShot shot(String id, long start, long end) {
        return new DirectorShot(id, start, end, "cam-" + id, null, DirectorShot.Type.STATIC, DirectorShot.Transition.CUT);
    }

    @Test
    void acceptsOnlyRenderableFiniteNonNegativeTicks() {
        DirectorPlan plan = new DirectorPlan();
        plan.add(shot("a", 0, 20));

        assertTrue(DirectorPlaybackTimeline.canStart(plan, 0));
        assertTrue(DirectorPlaybackTimeline.canStart(plan, 19.99));
        assertFalse(DirectorPlaybackTimeline.canStart(plan, 20));
        assertFalse(DirectorPlaybackTimeline.canStart(plan, -1));
        assertFalse(DirectorPlaybackTimeline.canStart(plan, Double.NaN));
        assertFalse(DirectorPlaybackTimeline.canStart(plan, Double.POSITIVE_INFINITY));
    }

    @Test
    void seekRejectsGapsAndPlanEnd() {
        DirectorPlan plan = new DirectorPlan();
        plan.add(shot("a", 0, 10));
        plan.add(shot("b", 20, 30));

        assertTrue(DirectorPlaybackTimeline.canSeek(plan, 5));
        assertFalse(DirectorPlaybackTimeline.canSeek(plan, 10));
        assertFalse(DirectorPlaybackTimeline.canSeek(plan, 15));
        assertTrue(DirectorPlaybackTimeline.canSeek(plan, 20));
        assertFalse(DirectorPlaybackTimeline.canSeek(plan, 30));
    }

    @Test
    void advancesWithinShotAndStopsAtBoundaryOrGap() {
        DirectorPlan continuous = new DirectorPlan();
        continuous.add(shot("a", 0, 20));
        continuous.add(shot("b", 20, 40));

        assertEquals(1.0, DirectorPlaybackTimeline.nextTick(continuous, 0).orElseThrow());
        assertEquals(20.0, DirectorPlaybackTimeline.nextTick(continuous, 19).orElseThrow());
        assertEquals(21.0, DirectorPlaybackTimeline.nextTick(continuous, 20).orElseThrow());
        assertTrue(DirectorPlaybackTimeline.nextTick(continuous, 39).isEmpty());

        DirectorPlan gap = new DirectorPlan();
        gap.add(shot("a", 0, 10));
        gap.add(shot("b", 20, 30));
        assertTrue(DirectorPlaybackTimeline.nextTick(gap, 9).isEmpty());
    }
}
