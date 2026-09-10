package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;

import java.util.Objects;

/** Pure shot-transition math. Keeps transition policy independent of Bukkit/NMS. */
public final class DirectorTransitionEvaluator {
    private DirectorTransitionEvaluator() { }

    public static final long DEFAULT_BLEND_TICKS = 10L;

    public record Result(CameraTransform transform, boolean transitioned, boolean fadeRequested, double progress) { }

    public static Result evaluate(DirectorShot current, DirectorShot previous,
                                  CameraTransform currentTransform, CameraTransform previousTransform,
                                  double sceneTick) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(currentTransform, "currentTransform");
        if (previous == null || previousTransform == null || current.transition() == DirectorShot.Transition.CUT) {
            return new Result(currentTransform, false, false, 1.0d);
        }
        long transitionTicks = Math.min(DEFAULT_BLEND_TICKS, Math.max(1L, current.durationTicks()));
        double progress = clamp((sceneTick - current.startTick()) / transitionTicks);
        if (current.transition() == DirectorShot.Transition.FADE) {
            return new Result(currentTransform, progress < 1.0d, true, progress);
        }
        return new Result(CameraTransform.interpolate(previousTransform, currentTransform, progress), progress < 1.0d, false, progress);
    }

    private static double clamp(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
