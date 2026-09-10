package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Immutable export manifest containing metadata and deterministic sampled frames. */
public final class DirectorExportManifest {
    private final DirectorExportSpec spec;
    private final List<DirectorFrame> frames;

    public DirectorExportManifest(DirectorExportSpec spec, List<DirectorFrame> frames) {
        this.spec = Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(frames, "frames");
        List<DirectorFrame> copy = new ArrayList<>(frames);
        long expected = 0L;
        for (DirectorFrame frame : copy) {
            Objects.requireNonNull(frame, "frame");
            if (frame.frameIndex() != expected) {
                throw new IllegalArgumentException("frames must be contiguous from index 0");
            }
            if (frame.frameIndex() >= spec.frameCount()) {
                throw new IllegalArgumentException("frame index exceeds export spec");
            }
            double expectedTick = spec.tickForFrame(frame.frameIndex());
            if (Math.abs(frame.sceneTick() - expectedTick) > 1e-9) {
                throw new IllegalArgumentException("frame tick does not match export spec");
            }
            expected++;
        }
        this.frames = List.copyOf(copy);
    }

    public DirectorExportSpec spec() { return spec; }
    public List<DirectorFrame> frames() { return frames; }
    public boolean complete() { return frames.size() == spec.frameCount(); }

    /** Serializes the manifest using only JDK APIs so the export layer has no JSON dependency. */
    public String toJson() {
        StringBuilder out = new StringBuilder(1024 + frames.size() * 180);
        out.append("{\n");
        field(out, "scene", spec.sceneName(), true);
        field(out, "startTick", spec.startTick(), true);
        field(out, "endTick", spec.endTick(), true);
        field(out, "fps", spec.fps(), true);
        field(out, "width", spec.width(), true);
        field(out, "height", spec.height(), true);
        field(out, "frameCount", spec.frameCount(), true);
        out.append("  \"frames\": [\n");
        for (int i = 0; i < frames.size(); i++) {
            DirectorFrame frame = frames.get(i);
            CameraTransform camera = frame.camera();
            out.append("    {\n");
            field(out, "index", frame.frameIndex(), true, 6);
            field(out, "tick", frame.sceneTick(), true, 6);
            out.append("      \"camera\": {\n");
            field(out, "x", camera.x(), true, 8);
            field(out, "y", camera.y(), true, 8);
            field(out, "z", camera.z(), true, 8);
            field(out, "yaw", camera.yaw(), true, 8);
            field(out, "pitch", camera.pitch(), true, 8);
            field(out, "roll", camera.roll(), true, 8);
            field(out, "fov", camera.fov(), false, 8);
            out.append("      }\n");
            out.append("    }").append(i + 1 < frames.size() ? "," : "").append("\n");
        }
        out.append("  ]\n");
        out.append("}\n");
        return out.toString();
    }

    private static void field(StringBuilder out, String name, String value, boolean comma) {
        out.append("  \"").append(escape(name)).append("\": \"").append(escape(value)).append("\"")
                .append(comma ? "," : "").append("\n");
    }

    private static void field(StringBuilder out, String name, long value, boolean comma) {
        out.append("  \"").append(name).append("\": ").append(value).append(comma ? "," : "").append("\n");
    }

    private static void field(StringBuilder out, String name, double value, boolean comma, int indent) {
        out.append(" ".repeat(indent)).append("\"").append(name).append("\": ")
                .append(number(value)).append(comma ? "," : "").append("\n");
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("non-finite camera value");
        return String.format(Locale.ROOT, "%.12f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
