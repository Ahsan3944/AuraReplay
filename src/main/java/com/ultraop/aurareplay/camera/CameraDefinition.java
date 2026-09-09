package com.ultraop.aurareplay.camera;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Editable cinematic camera definition. Keyframes are non-destructive scene data. */
public final class CameraDefinition {
    private final String id;
    private String name;
    private CameraTransform transform;
    private String followActorId;
    private boolean headTrack;
    private final List<CameraKeyframe> keyframes = new ArrayList<>();

    public CameraDefinition(String id, String name, CameraTransform transform) {
        this.id = normalize(id, "camera id");
        this.name = normalize(name, "camera name");
        this.transform = Objects.requireNonNull(transform, "transform");
    }

    public String id() { return id; }
    public String name() { return name; }
    public CameraTransform transform() { return transform; }
    public String followActorId() { return followActorId; }
    public boolean headTrack() { return headTrack; }
    public List<CameraKeyframe> keyframes() { return List.copyOf(keyframes); }

    public void setName(String name) { this.name = normalize(name, "camera name"); }
    public void setTransform(CameraTransform transform) { this.transform = Objects.requireNonNull(transform, "transform"); }
    public void setFollowActorId(String actorId) { this.followActorId = actorId == null || actorId.isBlank() ? null : actorId; }
    public void setHeadTrack(boolean headTrack) { this.headTrack = headTrack; }

    public void addKeyframe(CameraKeyframe keyframe) {
        Objects.requireNonNull(keyframe, "keyframe");
        keyframes.removeIf(existing -> existing.tick() == keyframe.tick());
        keyframes.add(keyframe);
        keyframes.sort(Comparator.comparingLong(CameraKeyframe::tick));
    }

    public boolean removeKeyframe(long tick) { return keyframes.removeIf(keyframe -> keyframe.tick() == tick); }

    /** Samples the camera using linear position/FOV interpolation and shortest-angle rotation. */
    public CameraTransform sample(double tick) {
        if (keyframes.isEmpty()) return transform;
        if (tick <= keyframes.get(0).tick()) return keyframes.get(0).transform();
        CameraKeyframe last = keyframes.get(keyframes.size() - 1);
        if (tick >= last.tick()) return last.transform();
        for (int i = 1; i < keyframes.size(); i++) {
            CameraKeyframe next = keyframes.get(i);
            CameraKeyframe previous = keyframes.get(i - 1);
            if (tick <= next.tick()) {
                double span = next.tick() - previous.tick();
                double alpha = span <= 0.0 ? 1.0 : (tick - previous.tick()) / span;
                return CameraTransform.interpolate(previous.transform(), next.transform(), alpha);
            }
        }
        return last.transform();
    }

    private static String normalize(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be empty");
        return normalized;
    }
}
