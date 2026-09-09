package com.ultraop.aurareplay.motion;

import com.ultraop.aurareplay.actor.ActorTransform;

import java.util.Objects;

/** Pure motion-layer evaluator. It never mutates or copies recording frames. */
public final class MotionEvaluator {
    public record Evaluation(double sourcePosition, ActorTransform transform, boolean loop, boolean reverse) {
        public Evaluation {
            if (!Double.isFinite(sourcePosition)) throw new IllegalArgumentException("sourcePosition must be finite");
            Objects.requireNonNull(transform, "transform");
        }
    }

    public Evaluation evaluate(double timelineTick, int frameCount, ActorTransform base, MotionStack stack) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(stack, "stack");
        if (frameCount <= 0 || !Double.isFinite(timelineTick)) return new Evaluation(0.0d, base, false, false);
        double position = Math.max(0.0d, timelineTick);
        double px = base.x(), py = base.y(), pz = base.z();
        float yaw = base.yaw(), pitch = base.pitch(), roll = base.roll();
        double scale = base.scale();
        boolean loop = false, reverse = false;
        double blend = 1.0d;
        for (MotionLayer layer : stack.layers()) {
            if (!layer.enabled()) continue;
            boolean active = layer.activeAt(timelineTick);
            switch (layer.type()) {
                case TIME_OFFSET -> { if (active) position += layer.value(); }
                case SPEED_MULTIPLIER -> { if (active) position *= layer.value(); }
                case REVERSE -> { if (active) reverse = !reverse; }
                case MIRROR_X -> { if (active) { position = mirrorPosition(position, frameCount); px = -px; yaw = normalize(yaw + 180.0f); } }
                case MIRROR_Z -> { if (active) { position = mirrorPosition(position, frameCount); pz = -pz; yaw = normalize(180.0f - yaw); } }
                case LOOP -> { if (active) loop = true; }
                case TRIM -> { if (active) position = clamp(position, layer.startTick(), layer.endTick()); }
                case RETIME -> { if (active) position = layer.startTick() + (position - layer.startTick()) * layer.value(); }
                case FREEZE_POSE, POSE_SNAPSHOT -> { if (active) position = Math.max(0.0d, layer.value()); }
                case BLEND -> { if (active) blend = clamp(layer.value(), 0.0d, 1.0d); }
                case POSITION_OFFSET, PATH_SHIFT -> { if (active) { px += layer.x() * blend; py += layer.y() * blend; pz += layer.z() * blend; } }
                case ROTATION_OFFSET -> { if (active) { yaw = normalize((float) (yaw + layer.yaw() * blend)); pitch = normalize((float) (pitch + layer.pitch() * blend)); roll = normalize((float) (roll + layer.roll() * blend)); } }
                case SCALE -> { if (active) scale *= layer.value(); }
            }
        }
        if (reverse) position = frameCount - 1.0d - position;
        if (loop) position = positiveModulo(position, frameCount);
        position = clamp(position, 0.0d, frameCount - 1.0d);
        return new Evaluation(position, new ActorTransform(px, py, pz, yaw, pitch, roll, scale), loop, reverse);
    }

    private static double mirrorPosition(double position, int frameCount) { return frameCount - 1.0d - position; }
    private static double positiveModulo(double value, double divisor) { double result = value % divisor; return result < 0.0d ? result + divisor : result; }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static float normalize(float value) { float n = value % 360.0f; if (n > 180.0f) n -= 360.0f; if (n <= -180.0f) n += 360.0f; return n; }
}
