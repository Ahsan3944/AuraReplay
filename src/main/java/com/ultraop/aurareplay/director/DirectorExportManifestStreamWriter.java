package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;

/**
 * Incrementally writes the same JSON manifest shape as {@link DirectorExportManifest}
 * without retaining the sampled frames in memory.
 */
public final class DirectorExportManifestStreamWriter implements DirectorCaptureSink {
    private final Path output;
    private BufferedWriter writer;
    private DirectorExportSpec spec;
    private long nextFrame;
    private boolean finished;

    public DirectorExportManifestStreamWriter(Path output) {
        this.output = Objects.requireNonNull(output, "output").toAbsolutePath().normalize();
    }

    public Path output() { return output; }
    public long nextFrameIndex() { return nextFrame; }

    @Override
    public void start(DirectorExportSpec spec) {
        if (writer != null || finished) throw new IllegalStateException("writer is not ready");
        this.spec = Objects.requireNonNull(spec, "spec");
        try {
            Path parent = output.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temp = tempPath();
            Files.deleteIfExists(temp);
            writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8);
            writer.write("{\n");
            field("scene", spec.sceneName(), true);
            field("startTick", spec.startTick(), true);
            field("endTick", spec.endTick(), true);
            field("fps", spec.fps(), true);
            field("width", spec.width(), true);
            field("height", spec.height(), true);
            field("frameCount", spec.frameCount(), true);
            writer.write("  \"frames\": [\n");
        } catch (IOException ex) {
            cleanupTemp();
            throw new IllegalStateException("failed to open export manifest", ex);
        }
    }

    @Override
    public void accept(DirectorFrame frame) {
        Objects.requireNonNull(frame, "frame");
        if (writer == null || spec == null) throw new IllegalStateException("writer is not running");
        if (frame.frameIndex() != nextFrame)
            throw new IllegalArgumentException("expected frame " + nextFrame + " but received " + frame.frameIndex());
        if (frame.frameIndex() >= spec.frameCount()) throw new IllegalArgumentException("frame index exceeds export spec");
        double expectedTick = spec.tickForFrame(frame.frameIndex());
        if (Math.abs(frame.sceneTick() - expectedTick) > 1.0e-9)
            throw new IllegalArgumentException("frame tick does not match export spec");
        try {
            if (nextFrame > 0) writer.write(",\n");
            writeFrame(frame);
            writer.flush();
            nextFrame++;
        } catch (IOException ex) {
            throw new IllegalStateException("failed to write export frame " + nextFrame, ex);
        }
    }

    @Override
    public void complete() {
        if (finished) return;
        if (writer == null || spec == null) throw new IllegalStateException("writer is not running");
        if (nextFrame != spec.frameCount()) throw new IllegalStateException("export is incomplete: " + nextFrame + "/" + spec.frameCount());
        try {
            writer.write("\n  ]\n}\n");
            writer.close();
            writer = null;
            moveIntoPlace();
            finished = true;
        } catch (IOException ex) {
            cleanupTemp();
            throw new IllegalStateException("failed to finalize export manifest", ex);
        }
    }

    @Override
    public void cancel() {
        closeAndDelete();
    }

    @Override
    public void fail(Throwable error) {
        closeAndDelete();
    }

    private void writeFrame(DirectorFrame frame) throws IOException {
        CameraTransform c = frame.camera();
        writer.write("    {\n");
        field("index", frame.frameIndex(), true, 6);
        field("tick", frame.sceneTick(), true, 6);
        writer.write("      \"camera\": {\n");
        field("x", c.x(), true, 8);
        field("y", c.y(), true, 8);
        field("z", c.z(), true, 8);
        field("yaw", c.yaw(), true, 8);
        field("pitch", c.pitch(), true, 8);
        field("roll", c.roll(), true, 8);
        field("fov", c.fov(), false, 8);
        writer.write("      }\n");
        writer.write("    }");
    }

    private void field(String name, String value, boolean comma) throws IOException {
        writer.write("  \"");
        writer.write(escape(name));
        writer.write("\": \"");
        writer.write(escape(value));
        writer.write("\"");
        if (comma) writer.write(',');
        writer.write('\n');
    }

    private void field(String name, long value, boolean comma) throws IOException {
        writer.write("  \"");
        writer.write(name);
        writer.write("\": ");
        writer.write(Long.toString(value));
        if (comma) writer.write(',');
        writer.write('\n');
    }

    private void field(String name, double value, boolean comma, int indent) throws IOException {
        writer.write(" ".repeat(indent));
        writer.write("\"");
        writer.write(name);
        writer.write("\": ");
        writer.write(number(value));
        if (comma) writer.write(',');
        writer.write('\n');
    }

    private Path tempPath() { return output.resolveSibling(output.getFileName() + ".tmp"); }

    private void moveIntoPlace() throws IOException {
        try {
            Files.move(tempPath(), output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tempPath(), output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void closeAndDelete() {
        try {
            if (writer != null) writer.close();
        } catch (IOException ignored) {
        } finally {
            writer = null;
            cleanupTemp();
        }
    }

    private void cleanupTemp() {
        try { Files.deleteIfExists(tempPath()); } catch (IOException ignored) { }
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
