package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectorTransitionEvaluatorTest {
    private static CameraTransform camera(double x) {
        return new CameraTransform(x, 64, 0, 0, 0, 0, 70);
    }

    private static DirectorShot shot(DirectorShot.Transition transition) {
        return new DirectorShot("shot", 20, 100, "cam", null, DirectorShot.Type.STATIC, transition);
    }

    @Test
    void cutUsesCurrentTransform() {
        DirectorTransitionEvaluator.Result result = DirectorTransitionEvaluator.evaluate(
                shot(DirectorShot.Transition.CUT), shot(DirectorShot.Transition.BLEND),
                camera(20), camera(0), 20);
        assertEquals(20, result.transform().x(), 1e-9);
        assertFalse(result.fadeRequested());
    }

    @Test
    void blendInterpolatesAcrossTransitionWindow() {
        DirectorTransitionEvaluator.Result result = DirectorTransitionEvaluator.evaluate(
                shot(DirectorShot.Transition.BLEND), shot(DirectorShot.Transition.CUT),
                camera(20), camera(0), 25);
        assertEquals(10, result.transform().x(), 1e-9);
        assertEquals(0.5, result.progress(), 1e-9);
        assertTrue(result.transitioned());
    }

    @Test
    void blendCompletesAfterTenTicks() {
        DirectorTransitionEvaluator.Result result = DirectorTransitionEvaluator.evaluate(
                shot(DirectorShot.Transition.BLEND), shot(DirectorShot.Transition.CUT),
                camera(20), camera(0), 30);
        assertEquals(20, result.transform().x(), 1e-9);
        assertFalse(result.transitioned());
    }

    @Test
    void fadeIsExplicitlySignalledWithoutFakingAnOverlay() {
        DirectorTransitionEvaluator.Result result = DirectorTransitionEvaluator.evaluate(
                shot(DirectorShot.Transition.FADE), shot(DirectorShot.Transition.CUT),
                camera(20), camera(0), 20);
        assertTrue(result.fadeRequested());
        assertEquals(20, result.transform().x(), 1e-9);
    }
}
