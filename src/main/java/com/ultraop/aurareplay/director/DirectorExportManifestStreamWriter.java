package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Objects;

/** Incrementally writes the Director JSON manifest and supports checkpoint resume. */
public final class DirectorExportManifestStreamWriter implements DirectorCaptureSink {
    private final Path output;
    private BufferedWriter writer;
    private DirectorExportSpec spec;
    private long nextFrame;
    private boolean finished;

    public DirectorExportManifestStreamWriter(Path output) { this.output = Objects.requireNonNull(output, "output").toAbsolutePath().normalize(); }
    public Path output() { return output; }
    public long nextFrameIndex() { return nextFrame; }
    @Override public void start(DirectorExportSpec spec) { open(spec, 0L, false); }
    public void resume(DirectorExportSpec spec, long nextFrameIndex) {
        Objects.requireNonNull(spec, "spec");
        if (nextFrameIndex < 0 || nextFrameIndex > spec.frameCount()) throw new IllegalArgumentException("nextFrameIndex must be within export frame range");
        open(spec, nextFrameIndex, true);
    }
    private void open(DirectorExportSpec value, long start, boolean resume) {
        if (writer != null || finished) throw new IllegalStateException("writer is not ready");
        spec = Objects.requireNonNull(value, "spec");
        try {
            Path parent = output.getParent(); if (parent != null) Files.createDirectories(parent);
            Path temp = tempPath();
            if (!resume) { Files.deleteIfExists(temp); writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8); writeHeader(); }
            else {
                if (!Files.exists(temp)) throw new IllegalStateException("export checkpoint has no temporary manifest");
                validateTempCheckpoint(temp, spec, start);
                truncateToFrame(temp, start);
                writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            }
            nextFrame = start;
        } catch (IOException ex) { cleanupTemp(); throw new IllegalStateException("failed to open export manifest", ex); }
    }
    private void writeHeader() throws IOException {
        writer.write("{\n"); field("scene", spec.sceneName(), true); field("startTick", spec.startTick(), true); field("endTick", spec.endTick(), true);
        field("fps", spec.fps(), true); field("width", spec.width(), true); field("height", spec.height(), true); field("frameCount", spec.frameCount(), true); writer.write("  \"frames\": [\n");
    }
    @Override public void accept(DirectorFrame frame) {
        Objects.requireNonNull(frame, "frame");
        if (writer == null || spec == null) throw new IllegalStateException("writer is not running");
        if (frame.frameIndex() != nextFrame) throw new IllegalArgumentException("expected frame " + nextFrame + " but received " + frame.frameIndex());
        if (frame.frameIndex() >= spec.frameCount()) throw new IllegalArgumentException("frame index exceeds export spec");
        if (Math.abs(frame.sceneTick() - spec.tickForFrame(frame.frameIndex())) > 1e-9) throw new IllegalArgumentException("frame tick does not match export spec");
        try { if (nextFrame > 0) writer.write(",\n"); writeFrame(frame); writer.flush(); nextFrame++; }
        catch (IOException ex) { throw new IllegalStateException("failed to write export frame " + nextFrame, ex); }
    }
    @Override public void complete() {
        if (finished) return;
        if (writer == null || spec == null) throw new IllegalStateException("writer is not running");
        if (nextFrame != spec.frameCount()) throw new IllegalStateException("export is incomplete: " + nextFrame + "/" + spec.frameCount());
        try { writer.write("\n  ]\n}\n"); writer.close(); writer = null; moveIntoPlace(); finished = true; }
        catch (IOException ex) { cleanupTemp(); throw new IllegalStateException("failed to finalize export manifest", ex); }
    }
    @Override public void pause() {
        if (writer == null || finished) return;
        try { writer.flush(); writer.close(); }
        catch (IOException ex) { throw new IllegalStateException("failed to pause export manifest", ex); }
        finally { writer = null; }
    }
    @Override public void cancel() { closeAndDelete(); }
    @Override public void fail(Throwable error) { closeAndDelete(); }
    private void writeFrame(DirectorFrame frame) throws IOException {
        CameraTransform c = frame.camera(); writer.write("    {\n"); field("index", frame.frameIndex(), true, 6); field("tick", frame.sceneTick(), true, 6); writer.write("      \"camera\": {\n");
        field("x", c.x(), true, 8); field("y", c.y(), true, 8); field("z", c.z(), true, 8); field("yaw", c.yaw(), true, 8); field("pitch", c.pitch(), true, 8); field("roll", c.roll(), true, 8); field("fov", c.fov(), false, 8); writer.write("      }\n"); writer.write("    }");
    }
    private void field(String n, String v, boolean comma) throws IOException { writer.write("  \""); writer.write(escape(n)); writer.write("\": \""); writer.write(escape(v)); writer.write("\""); if (comma) writer.write(','); writer.write('\n'); }
    private void field(String n, long v, boolean comma) throws IOException { writer.write("  \""); writer.write(n); writer.write("\": "); writer.write(Long.toString(v)); if (comma) writer.write(','); writer.write('\n'); }
    private void field(String n, double v, boolean comma, int indent) throws IOException { writer.write(" ".repeat(indent)); writer.write("\""); writer.write(n); writer.write("\": "); writer.write(number(v)); if (comma) writer.write(','); writer.write('\n'); }

    /** Validates a recoverable temporary manifest against its checkpoint state. */
    public static void validateCheckpoint(Path temp, DirectorExportSpec expected, long next) throws IOException {
        Objects.requireNonNull(temp, "temp"); Objects.requireNonNull(expected, "expected");
        validateTempCheckpoint(temp.toAbsolutePath().normalize(), expected, next);
    }

    private static void validateTempCheckpoint(Path temp, DirectorExportSpec expected, long next) throws IOException {
        String json = Files.readString(temp, StandardCharsets.UTF_8);
        if (!expected.sceneName().equals(stringField(json, "scene"))
                || expected.startTick() != longField(json, "startTick")
                || expected.endTick() != longField(json, "endTick")
                || expected.fps() != longField(json, "fps")
                || expected.width() != longField(json, "width")
                || expected.height() != longField(json, "height")
                || expected.frameCount() != longField(json, "frameCount"))
            throw new IllegalArgumentException("temporary manifest does not match export spec");
        long found = 0L;
        int cursor = json.indexOf("\"frames\": [");
        if (cursor < 0) throw new IllegalArgumentException("temporary manifest has invalid frame section");
        while (found < next) {
            int marker = json.indexOf("\"index\": " + found + ",", cursor);
            if (marker < 0) throw new IllegalArgumentException("temporary manifest is missing completed frame " + found);
            cursor = marker + 1; found++;
        }
        if (json.indexOf("\"index\": " + next + ",", cursor) >= 0) throw new IllegalArgumentException("temporary manifest contains uncheckpointed frame " + next);
    }

    private static String stringField(String json, String name) {
        String marker = "\"" + name + "\": \""; int start = json.indexOf(marker);
        if (start < 0) throw new IllegalArgumentException("missing temporary manifest field: " + name); start += marker.length();
        StringBuilder value = new StringBuilder(); boolean escaped = false;
        for (int i = start; i < json.length(); i++) { char c = json.charAt(i); if (escaped) { switch (c) { case '"' -> value.append('"'); case '\\' -> value.append('\\'); case 'n' -> value.append('\n'); case 'r' -> value.append('\r'); case 't' -> value.append('\t'); default -> throw new IllegalArgumentException("invalid manifest escape: " + c); } escaped = false; } else if (c == '\\') escaped = true; else if (c == '"') return value.toString(); else value.append(c); }
        throw new IllegalArgumentException("invalid temporary manifest field: " + name);
    }
    private static long longField(String json, String name) {
        String marker = "\"" + name + "\":"; int start = json.indexOf(marker);
        if (start < 0) throw new IllegalArgumentException("missing temporary manifest field: " + name); start += marker.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start; if (end < json.length() && json.charAt(end) == '-') end++; int digits = end;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        if (end == digits) throw new IllegalArgumentException("invalid temporary manifest field: " + name);
        return Long.parseLong(json.substring(start, end));
    }

    private static void truncateToFrame(Path temp, long next) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(temp.toFile(), "rw")) {
            if (next == 0) { String line; while ((line = raf.readLine()) != null) if (line.contains("\"frames\": [")) { raf.setLength(raf.getFilePointer()); return; } throw new IllegalArgumentException("temporary manifest has invalid header"); }
            long target = next - 1; raf.seek(0L); String line; long targetIndexLineStart = -1L;
            while (true) { long lineStart = raf.getFilePointer(); line = raf.readLine(); if (line == null) break; if (line.contains("\"index\": " + target + ",")) { targetIndexLineStart = lineStart; break; } }
            if (targetIndexLineStart < 0) throw new IllegalArgumentException("temporary manifest does not contain completed checkpoint frame " + target);
            long frameStart = targetIndexLineStart; while (frameStart > 0) { raf.seek(frameStart - 1); if (raf.read() == '{') break; frameStart--; }
            if (frameStart <= 0) throw new IllegalArgumentException("temporary manifest has invalid checkpoint frame boundary");
            long frameEnd = frameStart; raf.seek(frameStart); boolean inString = false; boolean escaped = false; int depth = 0;
            while (true) { int b = raf.read(); if (b < 0) throw new IllegalArgumentException("temporary manifest has incomplete checkpoint frame"); char c = (char) b; if (inString) { if (escaped) escaped = false; else if (c == '\\') escaped = true; else if (c == '"') inString = false; continue; } if (c == '"') { inString = true; continue; } if (c == '{') depth++; else if (c == '}') { depth--; if (depth == 0) { frameEnd = raf.getFilePointer(); break; } } }
            raf.setLength(frameEnd);
        }
    }
    private Path tempPath() { return output.resolveSibling(output.getFileName() + ".tmp"); }
    private void moveIntoPlace() throws IOException { try { Files.move(tempPath(), output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); } catch (AtomicMoveNotSupportedException ignored) { Files.move(tempPath(), output, StandardCopyOption.REPLACE_EXISTING); } }
    private void closeAndDelete() { try { if (writer != null) writer.close(); } catch (IOException ignored) {} finally { writer = null; cleanupTemp(); } }
    private void cleanupTemp() { try { Files.deleteIfExists(tempPath()); } catch (IOException ignored) {} }
    private static String number(double v) { if (!Double.isFinite(v)) throw new IllegalArgumentException("non-finite camera value"); return String.format(Locale.ROOT, "%.12f", v).replaceAll("0+$", "").replaceAll("\\.$", ""); }
    private static String escape(String v) { return v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"); }
}
