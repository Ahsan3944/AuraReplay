package com.ultraop.aurareplay.timeline;

/** Pure timeline math helpers kept independent from Bukkit/NMS. */
public final class TimelineMath {
    private TimelineMath() {}

    public static long trimDuration(Timeline timeline, long outPoint) {
        if (outPoint < 0 || outPoint > timeline.durationTicks()) throw new IllegalArgumentException("invalid trim point");
        return outPoint;
    }

    public static long stretchTicks(long tick, double factor) {
        if (!Double.isFinite(factor) || factor <= 0.0d) throw new IllegalArgumentException("factor must be finite and > 0");
        return Math.max(0L, Math.round(tick * factor));
    }

    public static double normalize(double tick, Timeline timeline) {
        if (timeline.outPoint() <= timeline.inPoint()) return timeline.inPoint();
        return Math.max(timeline.inPoint(), Math.min(timeline.outPoint(), tick));
    }
}
