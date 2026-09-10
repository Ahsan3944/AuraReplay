package com.ultraop.aurareplay.director;

import java.util.Objects;

/** Director shot with optional non-destructive camera path data. */
public record DirectorShot(
        String id,
        long startTick,
        long endTick,
        String cameraId,
        String targetActorId,
        Type type,
        Transition transition,
        DirectorPathTrack path
) {
    public enum Type { STATIC, FOLLOW, LOOK_AT, ORBIT, DOLLY, RAIL, SPLINE }
    public enum Transition { CUT, BLEND, FADE }

    public DirectorShot(String id, long startTick, long endTick, String cameraId,
                        String targetActorId, Type type, Transition transition) {
        this(id, startTick, endTick, cameraId, targetActorId, type, transition, new DirectorPathTrack());
    }

    public DirectorShot {
        id = normalize(id, "id");
        if (startTick < 0) throw new IllegalArgumentException("startTick must be >= 0");
        if (endTick <= startTick) throw new IllegalArgumentException("endTick must be > startTick");
        cameraId = normalize(cameraId, "cameraId");
        targetActorId = targetActorId == null || targetActorId.isBlank() ? null : targetActorId.trim();
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(transition, "transition");
        Objects.requireNonNull(path, "path");
    }

    public boolean contains(double tick) { return tick >= startTick && tick < endTick; }
    public boolean overlaps(DirectorShot other) { return startTick < other.endTick && other.startTick < endTick; }
    public long durationTicks() { return endTick - startTick; }

    private static String normalize(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be empty");
        return normalized;
    }
}
