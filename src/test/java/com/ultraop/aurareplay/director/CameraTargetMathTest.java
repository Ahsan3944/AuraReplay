package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CameraTargetMathTest {
    @Test
    void lookAtCalculatesMinecraftYawAndPitch() {
        CameraTransform camera = CameraTransform.origin(0, 0, 0, 0, 0);
        CameraTransform result = CameraTargetMath.lookAt(camera, 0, 0, 10);
        assertEquals(0f, result.yaw(), 1e-5f);
        assertEquals(0f, result.pitch(), 1e-5f);

        result = CameraTargetMath.lookAt(camera, 10, 0, 0);
        assertEquals(-90f, result.yaw(), 1e-5f);
    }

    @Test
    void followPreservesCameraRotationAndMovesToOffset() {
        CameraTransform camera = CameraTransform.origin(1, 2, 3, 45, 10);
        CameraTransform result = CameraTargetMath.follow(camera, 10, 20, 30, -2, 4, 5);
        assertEquals(8, result.x(), 1e-9);
        assertEquals(24, result.y(), 1e-9);
        assertEquals(35, result.z(), 1e-9);
        assertEquals(45, result.yaw());
        assertEquals(10, result.pitch());
    }
}
