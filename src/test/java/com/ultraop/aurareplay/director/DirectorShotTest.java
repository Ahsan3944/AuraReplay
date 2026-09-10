package com.ultraop.aurareplay.director;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectorShotTest {
    @Test
    void containsUsesStartInclusiveEndExclusive() {
        DirectorShot shot = new DirectorShot("s", 10, 20, "cam", null, DirectorShot.Type.STATIC, DirectorShot.Transition.CUT);
        assertFalse(shot.contains(9.99));
        assertTrue(shot.contains(10));
        assertTrue(shot.contains(19.99));
        assertFalse(shot.contains(20));
    }

    @Test
    void overlapChecksRanges() {
        DirectorShot a = new DirectorShot("a", 0, 10, "cam", null, DirectorShot.Type.STATIC, DirectorShot.Transition.CUT);
        DirectorShot b = new DirectorShot("b", 10, 20, "cam", null, DirectorShot.Type.STATIC, DirectorShot.Transition.CUT);
        DirectorShot c = new DirectorShot("c", 9, 20, "cam", null, DirectorShot.Type.STATIC, DirectorShot.Transition.CUT);
        assertFalse(a.overlaps(b));
        assertTrue(a.overlaps(c));
    }
}
