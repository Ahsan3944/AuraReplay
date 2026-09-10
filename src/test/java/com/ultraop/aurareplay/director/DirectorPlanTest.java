package com.ultraop.aurareplay.director;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectorPlanTest {
    private static DirectorShot shot(String id, long start, long end) {
        return new DirectorShot(id, start, end, "cam-" + id, null, DirectorShot.Type.STATIC, DirectorShot.Transition.CUT);
    }

    @Test
    void ordersShotsAndFindsActiveShot() {
        DirectorPlan plan = new DirectorPlan();
        plan.add(shot("b", 20, 40));
        plan.add(shot("a", 0, 20));

        assertEquals("a", plan.at(0).orElseThrow().id());
        assertEquals("a", plan.at(19.99).orElseThrow().id());
        assertEquals("b", plan.at(20).orElseThrow().id());
        assertEquals("b", plan.at(39).orElseThrow().id());
        assertTrue(plan.at(40).isEmpty());
    }

    @Test
    void rejectsOverlapAndDuplicateIds() {
        DirectorPlan plan = new DirectorPlan();
        plan.add(shot("a", 0, 20));
        assertThrows(IllegalArgumentException.class, () -> plan.add(shot("b", 19, 30)));
        assertThrows(IllegalArgumentException.class, () -> plan.add(shot("a", 20, 30)));
    }

    @Test
    void detectsGapsAndNeighbors() {
        DirectorPlan plan = new DirectorPlan();
        plan.add(shot("a", 0, 10));
        plan.add(shot("b", 20, 30));

        assertTrue(plan.hasGaps());
        assertEquals("b", plan.nextAfter(10).orElseThrow().id());
        assertEquals("a", plan.previousBefore(20).orElseThrow().id());
    }

    @Test
    void shotTransitionAndTargetAreRetained() {
        DirectorShot shot = new DirectorShot("hero", 10, 50, "main", "actor-1", DirectorShot.Type.FOLLOW, DirectorShot.Transition.BLEND);
        assertEquals(DirectorShot.Type.FOLLOW, shot.type());
        assertEquals(DirectorShot.Transition.BLEND, shot.transition());
        assertEquals("actor-1", shot.targetActorId());
        assertEquals(40, shot.durationTicks());
    }
}
