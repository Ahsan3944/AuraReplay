package com.ultraop.aurareplay.director;

import java.util.OptionalDouble;

/** Pure director timeline rules used by viewer-local playback. */
public final class DirectorPlaybackTimeline {
    private DirectorPlaybackTimeline() { }

    public static boolean canStart(DirectorPlan plan, double tick) {
        return validTick(tick) && plan != null && plan.at(tick).isPresent();
    }

    public static boolean canSeek(DirectorPlan plan, double tick) {
        return canStart(plan, tick);
    }

    /** Returns the next renderable scene tick, or empty at a shot boundary/end/gap. */
    public static OptionalDouble nextTick(DirectorPlan plan, double currentTick) {
        if (plan == null || !validTick(currentTick)) return OptionalDouble.empty();
        double next = currentTick + 1.0d;
        if (next >= plan.durationTicks() || plan.at(next).isEmpty()) return OptionalDouble.empty();
        return OptionalDouble.of(next);
    }

    private static boolean validTick(double tick) {
        return Double.isFinite(tick) && tick >= 0.0d;
    }
}
