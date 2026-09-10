package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;

import java.util.List;
import java.util.Objects;

/** Pure deterministic camera path evaluation for director shots. */
public final class DirectorPathEvaluator {
    private DirectorPathEvaluator() { }

    public record PathPoint(double tick, CameraTransform transform) {
        public PathPoint {
            if (!Double.isFinite(tick) || tick < 0.0) throw new IllegalArgumentException("tick must be finite and >= 0");
            Objects.requireNonNull(transform, "transform");
        }
    }

    public static CameraTransform evaluate(DirectorShot.Type type, List<PathPoint> points, double tick) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(points, "points");
        if (points.isEmpty()) throw new IllegalArgumentException("points cannot be empty");
        if (!Double.isFinite(tick)) throw new IllegalArgumentException("tick must be finite");
        if (points.size() == 1 || type == DirectorShot.Type.STATIC) return points.get(0).transform();
        if (tick <= points.get(0).tick()) return points.get(0).transform();
        if (tick >= points.get(points.size() - 1).tick()) return points.get(points.size() - 1).transform();

        for (int i = 1; i < points.size(); i++) {
            PathPoint b = points.get(i);
            PathPoint a = points.get(i - 1);
            if (tick <= b.tick()) {
                double span = b.tick() - a.tick();
                double alpha = span <= 0.0 ? 1.0 : (tick - a.tick()) / span;
                if (type == DirectorShot.Type.SPLINE && points.size() >= 3) {
                    PathPoint p0 = points.get(Math.max(0, i - 2));
                    PathPoint p3 = points.get(Math.min(points.size() - 1, i + 1));
                    return catmullRom(p0.transform(), a.transform(), b.transform(), p3.transform(), alpha);
                }
                return CameraTransform.interpolate(a.transform(), b.transform(), alpha);
            }
        }
        return points.get(points.size() - 1).transform();
    }

    private static CameraTransform catmullRom(CameraTransform p0, CameraTransform p1, CameraTransform p2, CameraTransform p3, double t) {
        double x = spline(p0.x(), p1.x(), p2.x(), p3.x(), t);
        double y = spline(p0.y(), p1.y(), p2.y(), p3.y(), t);
        double z = spline(p0.z(), p1.z(), p2.z(), p3.z(), t);
        CameraTransform linear = CameraTransform.interpolate(p1, p2, t);
        float yaw = linear.yaw();
        float pitch = linear.pitch();
        float roll = linear.roll();
        float fov = linear.fov();
        return new CameraTransform(x, y, z, yaw, pitch, roll, fov);
    }

    private static double spline(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2.0 * p1) + (-p0 + p2) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
    }
}
