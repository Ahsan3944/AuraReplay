package com.ultraop.aurareplay.effects;

import java.util.Objects;

/** Immutable timeline cue for viewer-local audiovisual effects. */
public record EffectCue(
        String id,
        long tick,
        Type type,
        double x,
        double y,
        double z,
        String key,
        float volume,
        float pitch,
        int count,
        double spread
) {
    public enum Type { PARTICLE, SOUND }

    public EffectCue {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id cannot be blank");
        if (tick < 0) throw new IllegalArgumentException("tick must be >= 0");
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("position must be finite");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key cannot be blank");
        if (!Float.isFinite(volume) || volume < 0) throw new IllegalArgumentException("volume must be finite and >= 0");
        if (!Float.isFinite(pitch) || pitch < 0) throw new IllegalArgumentException("pitch must be finite and >= 0");
        if (count < 1) throw new IllegalArgumentException("count must be >= 1");
        if (!Double.isFinite(spread) || spread < 0) throw new IllegalArgumentException("spread must be finite and >= 0");
    }

    public static EffectCue particle(String id, long tick, double x, double y, double z, String particle, int count, double spread) {
        return new EffectCue(id, tick, Type.PARTICLE, x, y, z, particle, 1f, 1f, count, spread);
    }

    public static EffectCue sound(String id, long tick, double x, double y, double z, String sound, float volume, float pitch) {
        return new EffectCue(id, tick, Type.SOUND, x, y, z, sound, volume, pitch, 1, 0d);
    }
}
