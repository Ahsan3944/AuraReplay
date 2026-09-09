package com.ultraop.aurareplay.timeline;

/** Deterministic scene-time cursor supporting range playback, looping and reverse. */
public final class TimelineCursor {
    private double tick;

    public double tick() { return tick; }

    public void reset(Timeline timeline) {
        tick = timeline.reverse() ? timeline.outPoint() : timeline.inPoint();
    }

    public void setTick(double tick, Timeline timeline) {
        this.tick = clamp(tick, timeline.inPoint(), timeline.outPoint());
    }

    public boolean advance(Timeline timeline) {
        double delta = timeline.playbackSpeed();
        tick += timeline.reverse() ? -delta : delta;
        if (timeline.loop()) {
            if (timeline.outPoint() <= timeline.inPoint()) {
                tick = timeline.inPoint();
                return false;
            }
            double length = timeline.outPoint() - timeline.inPoint();
            while (tick > timeline.outPoint()) tick -= length;
            while (tick < timeline.inPoint()) tick += length;
            return true;
        }
        if (tick >= timeline.outPoint()) { tick = timeline.outPoint(); return false; }
        if (tick <= timeline.inPoint()) { tick = timeline.inPoint(); return false; }
        return true;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
