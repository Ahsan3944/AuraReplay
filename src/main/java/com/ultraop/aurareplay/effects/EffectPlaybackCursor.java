package com.ultraop.aurareplay.effects;

/** Viewer-local cue traversal state for continuous timeline playback. */
public final class EffectPlaybackCursor {
    private static final double EPSILON = 1.0e-9d;
    private Double previousTick;
    private long loopGeneration;

    public void reset(double tick) { validate(tick); previousTick = tick; loopGeneration = 0L; }
    public void seek(double tick) { validate(tick); previousTick = tick; }
    public boolean initialized() { return previousTick != null; }
    public double previousTick() { return previousTick == null ? 0d : previousTick; }
    public long loopGeneration() { return loopGeneration; }
    public void nextLoop() { loopGeneration++; }
    public boolean crossedForward(double cueTick, double from, double to) { return cueTick > from + EPSILON && cueTick <= to + EPSILON; }
    public boolean crossedReverse(double cueTick, double from, double to) { return cueTick >= to - EPSILON && cueTick < from - EPSILON; }
    public void commit(double tick) { validate(tick); previousTick = tick; }
    private static void validate(double tick) { if (!Double.isFinite(tick)) throw new IllegalArgumentException("tick must be finite"); }
}
