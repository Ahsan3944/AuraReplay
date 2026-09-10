package com.ultraop.aurareplay.director;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Atomic sidecar persistence for restartable Director export checkpoints. */
public final class DirectorExportCheckpointStore {
    public Path save(Path checkpoint, DirectorExportCheckpoint value) throws IOException {
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(value, "value");
        Path target = checkpoint.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        String json = toJson(value);
        Files.writeString(temp, json, StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    public DirectorExportCheckpoint load(Path checkpoint, DirectorExportSpec expected) throws IOException {
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(expected, "expected");
        String json = Files.readString(checkpoint.toAbsolutePath().normalize(), StandardCharsets.UTF_8);
        String scene = stringField(json, "scene");
        long start = longField(json, "startTick");
        long end = longField(json, "endTick");
        int fps = Math.toIntExact(longField(json, "fps"));
        int width = Math.toIntExact(longField(json, "width"));
        int height = Math.toIntExact(longField(json, "height"));
        long next = longField(json, "nextFrameIndex");
        DirectorExportSpec actual = new DirectorExportSpec(scene, start, end, fps, width, height);
        if (!actual.equals(expected)) throw new IllegalArgumentException("checkpoint does not match export spec");
        return new DirectorExportCheckpoint(expected, next);
    }

    public void delete(Path checkpoint) throws IOException {
        if (checkpoint != null) Files.deleteIfExists(checkpoint.toAbsolutePath().normalize());
    }

    private static String toJson(DirectorExportCheckpoint value) {
        DirectorExportSpec s = value.spec();
        return "{\n" +
                "  \"scene\": \"" + escape(s.sceneName()) + "\",\n" +
                "  \"startTick\": " + s.startTick() + ",\n" +
                "  \"endTick\": " + s.endTick() + ",\n" +
                "  \"fps\": " + s.fps() + ",\n" +
                "  \"width\": " + s.width() + ",\n" +
                "  \"height\": " + s.height() + ",\n" +
                "  \"nextFrameIndex\": " + value.nextFrameIndex() + "\n" +
                "}\n";
    }

    private static String stringField(String json, String name) {
        String marker = "\"" + name + "\": \"";
        int start = json.indexOf(marker);
        if (start < 0) throw new IllegalArgumentException("missing checkpoint field: " + name);
        start += marker.length();

        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case '"' -> value.append('"');
                    case '\\' -> value.append('\\');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    default -> throw new IllegalArgumentException("invalid checkpoint escape character: " + c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return value.toString();
            } else {
                value.append(c);
            }
        }
        throw new IllegalArgumentException("invalid checkpoint field: " + name);
    }

    private static long longField(String json, String name) {
        String marker = "\"" + name + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            marker = "\"" + name + "\": ";
            start = json.indexOf(marker);
        }
        if (start < 0) throw new IllegalArgumentException("missing checkpoint field: " + name);
        start += marker.length();
        int end = start;
        while (end < json.length() && "0123456789-".indexOf(json.charAt(end)) >= 0) end++;
        if (end == start) throw new IllegalArgumentException("invalid checkpoint field: " + name);
        return Long.parseLong(json.substring(start, end));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
