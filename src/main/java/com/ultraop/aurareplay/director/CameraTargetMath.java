package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;

/** Pure camera targeting math; no Bukkit/NMS dependency. */
public final class CameraTargetMath {
    private CameraTargetMath() { }

    public static CameraTransform lookAt(CameraTransform camera, double targetX, double targetY, double targetZ) {
        double dx = targetX - camera.x();
        double dy = targetY - camera.y();
        double dz = targetZ - camera.z();
        double horizontal = Math.hypot(dx, dz);
        if (horizontal < 1.0e-9 && Math.abs(dy) < 1.0e-9) return camera;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontal));
        return camera.withRotation(yaw, pitch, camera.roll());
    }

    public static CameraTransform follow(CameraTransform camera, double targetX, double targetY, double targetZ, double offsetX, double offsetY, double offsetZ) {
        return camera.withPosition(targetX + offsetX, targetY + offsetY, targetZ + offsetZ);
    }
}
