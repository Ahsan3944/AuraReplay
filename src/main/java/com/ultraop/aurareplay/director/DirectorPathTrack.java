package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Editable non-destructive path data owned by a director shot. */
public final class DirectorPathTrack {
    private final List<DirectorPathEvaluator.PathPoint> points = new ArrayList<>();

    public List<DirectorPathEvaluator.PathPoint> points() { return List.copyOf(points); }

    public void setPoint(long tick, CameraTransform transform) {
        if (tick < 0) throw new IllegalArgumentException("tick must be >= 0");
        Objects.requireNonNull(transform, "transform");
        points.removeIf(point -> point.tick() == tick);
        points.add(new DirectorPathEvaluator.PathPoint(tick, transform));
        points.sort(Comparator.comparingDouble(DirectorPathEvaluator.PathPoint::tick));
    }

    public boolean removePoint(long tick) { return points.removeIf(point -> point.tick() == tick); }

    public void clear() { points.clear(); }

    public CameraTransform sample(DirectorShot.Type type, double tick, CameraTransform fallback) {
        return points.isEmpty() ? fallback : DirectorPathEvaluator.evaluate(type, points, tick);
    }
}
