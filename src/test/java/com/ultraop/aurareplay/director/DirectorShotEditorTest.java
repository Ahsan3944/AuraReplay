package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectorShotEditorTest {
    private static CameraTransform camera() { return CameraTransform.origin(0, 64, 0, 0, 0); }

    @Test
    void editsTypeAndTransition() {
        DirectorPlan plan = new DirectorPlan();
        DirectorShot shot = new DirectorShot("s", 0, 40, "cam", null, DirectorShot.Type.STATIC, DirectorShot.Transition.CUT);
        plan.add(shot);
        DirectorShotEditor editor = new DirectorShotEditor(plan);
        assertTrue(editor.select("s"));
        assertTrue(editor.cycleType());
        assertEquals(DirectorShot.Type.FOLLOW, editor.selected().type());
        assertTrue(editor.cycleTransition());
        assertEquals(DirectorShot.Transition.BLEND, editor.selected().transition());
    }

    @Test
    void editsTargetAndPath() {
        DirectorPlan plan = new DirectorPlan();
        plan.add(new DirectorShot("s", 0, 40, "cam", null, DirectorShot.Type.DOLLY, DirectorShot.Transition.CUT));
        DirectorShotEditor editor = new DirectorShotEditor(plan);
        assertTrue(editor.select("s"));
        assertTrue(editor.setTarget("actor"));
        assertTrue(editor.capturePathPoint(10, camera()));
        assertEquals("actor", editor.selected().targetActorId());
        assertEquals(1, editor.selected().path().points().size());
    }

    @Test
    void rangeEditPreservesPath() {
        DirectorPlan plan = new DirectorPlan();
        DirectorShot shot = new DirectorShot("s", 0, 40, "cam", null, DirectorShot.Type.SPLINE, DirectorShot.Transition.CUT);
        shot.path().setPoint(5, camera());
        plan.add(shot);
        DirectorShotEditor editor = new DirectorShotEditor(plan);
        editor.select("s");
        assertTrue(editor.setRange(0, 60));
        assertEquals(60, editor.selected().endTick());
        assertEquals(1, editor.selected().path().points().size());
    }
}
