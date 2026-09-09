package com.ultraop.aurareplay.camera;

/** Immutable camera transform used by the cinematic camera system. */
public record CameraTransform(
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float roll,
        float fov
) {
    public static final float DEFAULT_FOV = 70.0f;

    public CameraTransform {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("position must be finite");
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)) throw new IllegalArgumentException("rotation must be finite");
        if (!Float.isFinite(fov) || fov <= 1.0f || fov > 179.0f) throw new IllegalArgumentException("fov must be > 1 and <= 179");
    }

    public static CameraTransform origin(double x, double y, double z, float yaw, float pitch) {
        return new CameraTransform(x, y, z, yaw, pitch, 0.0f, DEFAULT_FOV);
    }

    public CameraTransform withPosition(double x, double y, double z) {
        return new CameraTransform(x, y, z, yaw, pitch, roll, fov);
    }

    public CameraTransform withRotation(float yaw, float pitch, float roll) {
        return new CameraTransform(x, y, z, yaw, pitch, roll, fov);
    }

    public CameraTransform withFov(float fov) {
        return new CameraTransform(x, y, z, yaw, pitch, roll, fov);
    }

    public static CameraTransform interpolate(CameraTransform a, CameraTransform b, double alpha) {
        double t = Math.max(0.0, Math.min(1.0, alpha));
        return new CameraTransform(
                lerp(a.x, b.x, t), lerp(a.y, b.y, t), lerp(a.z, b.z, t),
                lerpAngle(a.yaw, b.yaw, t),
                (float) lerp(a.pitch, b.pitch, t),
                lerpAngle(a.roll, b.roll, t),
                (float) lerp(a.fov, b.fov, t));
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private static float lerpAngle(float a, float b, double t) {
        float delta = wrapDegrees(b - a);
        return (float) (a + delta * t);
    }

    private static float wrapDegrees(float angle) {
        float wrapped = angle % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }
}
