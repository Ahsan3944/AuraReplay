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
 * Incremental JSONL capture sink for realtime Director rendering.
 * Frames are streamed to a temporary file and atomically finalized on completion.
 */
public final class DirectorFrameStreamWriter implements DirectorCaptureSink {
    private final Path output;
    private BufferedWriter writer;
    private Path temp;
    private DirectorExportSpec spec;
    private long nextFrame;
    private boolean terminal;

    public DirectorFrameStreamWriter(Path output) {
        this.output = Objects.requireNonNull(output, "output").toAbsolutePath().normalize();
    }

    public Path output() { return output; }
    public long framesWritten() { return nextFrame; }
    public boolean open() { return writer != null && !terminal; }

    @Override
    public void start(DirectorExportSpec spec) {
        Objects.requireNonNull(spec, "spec");
        if (writer != null || terminal) throw new IllegalStateException("stream is not ready");
        try {
            Path parent = output.getParent();
            if (parent != null) Files.createDirectories(parent);
            temp = output.resolveSibling(output.getFileName() + ".tmp");
            writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8);
            this.spec = spec;
            nextFrame = 0L;
            writer.write(header(spec));
            writer.newLine();
            writer.flush();
        } catch (IOException ex) {
            closeQuietly();
            terminal = true;
            throw new IllegalStateException("Unable to open capture stream: " + output, ex);
        }
    }

    @Override
    public void accept(DirectorFrame frame) {
        Objects.requireNonNull(frame, "frame");
        if (!open()) throw new IllegalStateException("stream is not open");
        if (frame.frameIndex() != nextFrame) {
            throw new IllegalArgumentException("expected frame " + nextFrame + " but received " + frame.frameIndex());
        }
        if (Math.abs(frame.sceneTick() - spec.tickForFrame(nextFrame)) > 1.0e-9) {
            throw new IllegalArgumentException("frame tick does not match export spec");
        }
        try {
            writer.write(frameJson(frame));
            writer.newLine();
            writer.flush();
            nextFrame++;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to write capture frame " + nextFrame, ex);
        }
    }

    @Override
    public void complete() {
        if (terminal) return;
        if (!open()) throw new IllegalStateException("stream is not open");
        if (nextFrame != spec.frameCount()) {
            throw new IllegalStateException("capture is incomplete: " + nextFrame + "/" + spec.frameCount());
        }
        try {
            writer.flush();
            writer.close();
            moveIntoPlace();
            terminal = true;
        } catch (IOException ex) {
            closeQuietly();
            terminal = true;
            throw new IllegalStateException("Unable to finalize capture: " + output, ex);
        }
    }

    @Override
    public void cancel() {
        if (terminal) return;
        closeQuietly();
        deleteTempQuietly();
        terminal = true;
    }

    @Override
    public void fail(Throwable error) {
        Objects.requireNonNull(error, "error");
        if (terminal) return;
        closeQuietly();
        deleteTempQuietly();
        terminal = true;
    }

    private void moveIntoPlace() throws IOException {
        try {
            Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING);
        }
        temp = null;
    }

    private static String header(DirectorExportSpec spec) {
        return "{\"type\":\"director-stream\",\"scene\":\"" + escape(spec.sceneName())
                + "\",\"startTick\":" + spec.startTick()
                + ",\"endTick\":" + spec.endTick()
                + ",\"fps\":" + spec.fps()
                + ",\"width\":" + spec.width()
                + ",\"height\":" + spec.height()
                + ",\"frameCount\":" + spec.frameCount() + "}";
    }

    private static String frameJson(DirectorFrame frame) {
        CameraTransform c = frame.camera();
        return "{\"type\":\"frame\",\"index\":" + frame.frameIndex()
                + ",\"tick\":" + number(frame.sceneTick())
                + ",\"camera\":{"x\":" + number(c.x())
                + ",\"y\":" + number(c.y())
                + ",\"z\":" + number(c.z())
                + ",\"yaw\":" + number(c.yaw())
                + ",\"pitch\":" + number(c.pitch())
                + ",\"roll\":" + number(c.roll())
                + ",\"fov\":" + number(c.fov()) + "}}";
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("non-finite value");
        return String.format(Locale.ROOT, "%.12f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private void closeQuietly() {
        if (writer == null) return;
        try { writer.close(); } catch (IOException ignored) { }
        writer = null;
    }

    private void deleteTempQuietly() {
        if (temp == null) return;
        try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
        temp = null;
    }
}
