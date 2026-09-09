package com.ultraop.aurareplay.motion;

import java.util.Objects;

/** Immutable non-destructive motion operation. No recording data is copied. */
public record MotionLayer(
        String id,
        Type type,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float roll,
        double value,
        long startTick,
        long endTick,
        boolean enabled
) {
    public enum Type {
        POSITION_OFFSET,
        ROTATION_OFFSET,
        SCALE,
        TIME_OFFSET,
        SPEED_MULTIPLIER,
        REVERSE,
        MIRROR_X,
        MIRROR_Z,
        PATH_SHIFT,
        LOOP,
        BLEND,
        TRIM,
        RETIME,
        FREEZE_POSE,
        POSE_SNAPSHOT
    }

    public MotionLayer {
        id = Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        type = Objects.requireNonNull(type, "type");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)
                || !Double.isFinite(value)) {
            throw new IllegalArgumentException("motion values must be finite");
        }
        if (startTick < 0L || endTick < startTick) throw new IllegalArgumentException("invalid layer range");
        if ((type == Type.SCALE || type == Type.SPEED_MULTIPLIER || type == Type.BLEND || type == Type.RETIME)
                && value <= 0.0d) {
            throw new IllegalArgumentException("layer value must be > 0 for " + type);
        }
    }

    public boolean activeAt(double tick) {
        return enabled && Double.isFinite(tick) && tick >= startTick && tick <= endTick;
    }

    public static MotionLayer positionOffset(String id, double x, double y, double z, long start, long end) {
        return new MotionLayer(id, Type.POSITION_OFFSET, x, y, z, 0, 0, 0, 0, start, end, true);
    }

    public static MotionLayer rotationOffset(String id, float yaw, float pitch, float roll, long start, long end) {
        return new MotionLayer(id, Type.ROTATION_OFFSET, 0, 0, 0, yaw, pitch, roll, 0, start, end, true);
    }

    public static MotionLayer scale(String id, double scale, long start, long end) {
        return new MotionLayer(id, Type.SCALE, 0, 0, 0, 0, 0, 0, scale, start, end, true);
    }

    public static MotionLayer timeOffset(String id, double ticks, long start, long end) {
        return new MotionLayer(id, Type.TIME_OFFSET, 0, 0, 0, 0, 0, 0, ticks, start, end, true);
    }

    public static MotionLayer speed(String id, double multiplier, long start, long end) {
        return new MotionLayer(id, Type.SPEED_MULTIPLIER, 0, 0, 0, 0, 0, 0, multiplier, start, end, true);
    }

    public static MotionLayer reverse(String id, long start, long end) {
        return new MotionLayer(id, Type.REVERSE, 0, 0, 0, 0, 0, 0, 1, start, end, true);
    }

    public static MotionLayer mirrorX(String id, long start, long end) {
        return new MotionLayer(id, Type.MIRROR_X, 0, 0, 0, 0, 0, 0, 1, start, end, true);
    }

    public static MotionLayer mirrorZ(String id, long start, long end) {
        return new MotionLayer(id, Type.MIRROR_Z, 0, 0, 0, 0, 0, 0, 1, start, end, true);
    }

    public static MotionLayer pathShift(String id, double x, double y, double z, long start, long end) {
        return new MotionLayer(id, Type.PATH_SHIFT, x, y, z, 0, 0, 0, 0, start, end, true);
    }

    public static MotionLayer loop(String id, double repeatCount, long start, long end) {
        return new MotionLayer(id, Type.LOOP, 0, 0, 0, 0, 0, 0, repeatCount, start, end, true);
    }

    public static MotionLayer blend(String id, double weight, long start, long end) {
        return new MotionLayer(id, Type.BLEND, 0, 0, 0, 0, 0, 0, weight, start, end, true);
    }

    public static MotionLayer trim(String id, long sourceStart, long sourceEnd) {
        return new MotionLayer(id, Type.TRIM, 0, 0, 0, 0, 0, 0, 0, sourceStart, sourceEnd, true);
    }

    public static MotionLayer retime(String id, double multiplier, long start, long end) {
        return new MotionLayer(id, Type.RETIME, 0, 0, 0, 0, 0, 0, multiplier, start, end, true);
    }

    public static MotionLayer freezePose(String id, double sourceTick, long start, long end) {
        return new MotionLayer(id, Type.FREEZE_POSE, 0, 0, 0, 0, 0, 0, sourceTick, start, end, true);
    }

    public static MotionLayer poseSnapshot(String id, double sourceTick, long start, long end) {
        return new MotionLayer(id, Type.POSE_SNAPSHOT, 0, 0, 0, 0, 0, 0, sourceTick, start, end, true);
    }
}
