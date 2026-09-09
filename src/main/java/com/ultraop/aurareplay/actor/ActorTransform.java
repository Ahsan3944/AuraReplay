package com.ultraop.aurareplay.actor;

public record ActorTransform(
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float roll,
        double scale
) {
    public static ActorTransform origin(double x, double y, double z, float yaw, float pitch) {
        return new ActorTransform(x, y, z, yaw, pitch, 0.0f, 1.0d);
    }

    public ActorTransform withPosition(double x, double y, double z) {
        return new ActorTransform(x, y, z, yaw, pitch, roll, scale);
    }

    public ActorTransform withRotation(float yaw, float pitch, float roll) {
        return new ActorTransform(x, y, z, yaw, pitch, roll, scale);
    }

    public ActorTransform withScale(double scale) {
        if (scale <= 0.0d) throw new IllegalArgumentException("scale must be > 0");
        return new ActorTransform(x, y, z, yaw, pitch, roll, scale);
    }
}
