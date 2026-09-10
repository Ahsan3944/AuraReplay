package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DirectorPathEvaluatorTest {
    private static CameraTransform camera(double x, double z) {
        return new CameraTransform(x, 64, z, 0, 0, 0, 70);
    }

    private static List<DirectorPathEvaluator.PathPoint> points() {
        return List.of(
                new DirectorPathEvaluator.PathPoint(0, camera(0, 0)),
                new DirectorPathEvaluator.PathPoint(10, camera(10, 0)),
                new DirectorPathEvaluator.PathPoint(20, camera(20, 10)),
                new DirectorPathEvaluator.PathPoint(30, camera(30, 10)));
    }

    @Test
    void dollyInterpolatesBetweenPathPoints() {
        CameraTransform result = DirectorPathEvaluator.evaluate(DirectorShot.Type.DOLLY, points(), 5);
        assertEquals(5, result.x(), 1e-9);
        assertEquals(0, result.z(), 1e-9);
    }

    @Test
    void railInterpolatesThreeDimensionalPosition() {
        CameraTransform result = DirectorPathEvaluator.evaluate(DirectorShot.Type.RAIL, points(), 15);
        assertEquals(15, result.x(), 1e-9);
        assertEquals(5, result.z(), 1e-9);
    }

    @Test
    void splineProducesContinuousCurve() {
        CameraTransform result = DirectorPathEvaluator.evaluate(DirectorShot.Type.SPLINE, points(), 15);
        assertTrue(result.x() > 10 && result.x() < 20);
        assertTrue(result.z() >= 0 && result.z() <= 10);
    }

    @Test
    void clampsOutsidePath() {
        assertEquals(0, DirectorPathEvaluator.evaluate(DirectorShot.Type.DOLLY, points(), -5).x(), 1e-9);
        assertEquals(30, DirectorPathEvaluator.evaluate(DirectorShot.Type.DOLLY, points(), 50).x(), 1e-9);
    }
}
