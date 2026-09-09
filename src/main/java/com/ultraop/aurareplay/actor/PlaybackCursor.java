package com.ultraop.aurareplay.actor;

/** Deterministic sub-tick playback cursor. Position is expressed in frame ticks. */
public final class PlaybackCursor {
    private double position;

    public double position() { return position; }

    public void reset() { position = 0.0d; }

    public void setPosition(double position) {
        if (!Double.isFinite(position)) throw new IllegalArgumentException("position must be finite");
        this.position = Math.max(0.0d, position);
    }

    /** Advances by a fractional tick and applies loop/end semantics deterministically. */
    public void advance(double deltaTicks, double speed, boolean reverse, long durationTicks, boolean loop) {
        if (!Double.isFinite(deltaTicks) || !Double.isFinite(speed)) throw new IllegalArgumentException("deltaTicks and speed must be finite");
        if (deltaTicks < 0.0d) throw new IllegalArgumentException("deltaTicks must be >= 0");
        if (speed < 0.0d) throw new IllegalArgumentException("speed must be >= 0");
        double duration = Math.max(0L, durationTicks);
        if (duration <= 0.0d) { position = 0.0d; return; }

        double delta = deltaTicks * speed;
        if (reverse) position -= delta;
        else position += delta;

        if (loop) {
            position %= duration;
            if (position < 0.0d) position += duration;
            return;
        }

        double lastFrame = Math.max(0.0d, duration - 1.0d);
        position = Math.max(0.0d, Math.min(position, lastFrame));
    }

    /** Seeks to an exact sub-tick position without applying playback rules. */
    public void seek(double position) { setPosition(position); }

    /** Returns the integer frame immediately before the cursor. */
    public int lowerFrame() { return (int) Math.floor(position); }

    /** Returns the next frame, clamped to the supplied frame count. */
    public int upperFrame(int frameCount) {
        if (frameCount <= 0) return 0;
        return Math.min(frameCount - 1, lowerFrame() + 1);
    }

    /** Interpolation alpha between lowerFrame() and upperFrame(). */
    public double alpha() { return position - Math.floor(position); }
}
