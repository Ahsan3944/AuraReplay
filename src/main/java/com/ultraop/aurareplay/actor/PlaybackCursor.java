package com.ultraop.aurareplay.actor;

public final class PlaybackCursor {
    private double position;

    public double position() { return position; }

    public void reset() { position = 0.0d; }

    public void advance(double deltaTicks, double speed, boolean reverse, long durationTicks, boolean loop) {
        double delta = Math.max(0.0d, deltaTicks) * Math.max(0.0d, speed);
        position += reverse ? -delta : delta;
        if (loop && durationTicks > 0) {
            position %= durationTicks;
            if (position < 0) position += durationTicks;
        } else {
            position = Math.max(0.0d, Math.min(position, Math.max(0L, durationTicks - 1L)));
        }
    }
}
