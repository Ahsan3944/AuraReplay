package com.ultraop.aurareplay.storage;

import com.ultraop.aurareplay.recording.Recording;
import com.ultraop.aurareplay.recording.RecordingBinaryCodec;
import com.ultraop.aurareplay.recording.RecordingManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Durable .arrec recording files plus a SQLite metadata index. */
public final class RecordingStorageManager implements AutoCloseable {
    private static final int FORMAT_VERSION = 2;
    private final File databaseFile;
    private final Path recordingsDirectory;
    private final RecordingBinaryCodec codec;
    private final Executor executor;

    public RecordingStorageManager(JavaPlugin plugin, Executor executor) {
        Objects.requireNonNull(plugin, "plugin"); this.executor = Objects.requireNonNull(executor, "executor");
        File data = plugin.getDataFolder();
        if (!data.exists() && !data.mkdirs()) throw new IllegalStateException("Could not create plugin data directory");
        databaseFile = new File(data, "aurareplay.db"); recordingsDirectory = new File(data, "recordings").toPath();
        try { Files.createDirectories(recordingsDirectory); } catch (IOException e) { throw new IllegalStateException("Could not create recordings directory", e); }
        codec = new RecordingBinaryCodec(); initialize();
    }

    public CompletableFuture<List<RecordingMetadata>> listAsync() { return CompletableFuture.supplyAsync(this::list, executor); }
    public List<RecordingMetadata> list() {
        List<RecordingMetadata> result = new ArrayList<>();
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement("SELECT name,file_name,format_version,duration_ticks,frame_count,size_bytes,created_at,updated_at FROM recordings ORDER BY name"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(metadata(rs)); return List.copyOf(result);
        } catch (SQLException e) { throw failure("Could not list recordings", e); }
    }

    public CompletableFuture<Integer> loadAllAsync(RecordingManager manager) {
        Objects.requireNonNull(manager, "manager");
        return CompletableFuture.supplyAsync(() -> { int loaded = 0; for (RecordingMetadata m : list()) { try { manager.register(load(m)); loaded++; } catch (RuntimeException | IOException ignored) { } } return loaded; }, executor);
    }

    public CompletableFuture<Recording> loadAsync(String name) {
        return CompletableFuture.supplyAsync(() -> { try { return load(name); } catch (IOException e) { throw failure("Could not load recording " + name, e); } }, executor);
    }
    public Recording load(String name) throws IOException { return load(findMetadata(name).orElseThrow(() -> new IOException("recording not found: " + name))); }
    private Recording load(RecordingMetadata metadata) throws IOException {
        Path path = recordingsDirectory.resolve(metadata.fileName());
        if (!Files.isRegularFile(path)) throw new IOException("recording file missing: " + path.getFileName());
        return codec.decode(Files.readAllBytes(path));
    }

    public CompletableFuture<RecordingMetadata> saveAsync(Recording recording) { return CompletableFuture.supplyAsync(() -> save(recording), executor); }
    public RecordingMetadata save(Recording recording) {
        Objects.requireNonNull(recording, "recording");
        String normalized = normalize(recording.name()); String fileName = fileNameFor(normalized);
        Path target = recordingsDirectory.resolve(fileName); Path temp = recordingsDirectory.resolve(fileName + ".tmp");
        try {
            byte[] encoded = codec.encode(recording); Files.write(temp, encoded); moveAtomically(temp, target); long now = Instant.now().toEpochMilli();
            try (Connection c = open(); PreparedStatement ps = c.prepareStatement("INSERT INTO recordings(name,file_name,format_version,duration_ticks,frame_count,size_bytes,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(name) DO UPDATE SET file_name=excluded.file_name,format_version=excluded.format_version,duration_ticks=excluded.duration_ticks,frame_count=excluded.frame_count,size_bytes=excluded.size_bytes,updated_at=excluded.updated_at")) {
                Optional<RecordingMetadata> old = findMetadata(normalized, c);
                ps.setString(1, normalized); ps.setString(2, fileName); ps.setInt(3, FORMAT_VERSION); ps.setLong(4, recording.durationTicks()); ps.setInt(5, recording.frames().size()); ps.setLong(6, encoded.length); ps.setLong(7, old.map(RecordingMetadata::createdAt).orElse(now)); ps.setLong(8, now); ps.executeUpdate();
            }
            return findMetadata(normalized).orElseThrow(() -> new IllegalStateException("recording index write failed"));
        } catch (IOException | SQLException e) { try { Files.deleteIfExists(temp); } catch (IOException ignored) { } throw failure("Could not save recording " + recording.name(), e); }
    }

    public CompletableFuture<Boolean> deleteAsync(String name) { return CompletableFuture.supplyAsync(() -> delete(name), executor); }
    public boolean delete(String name) {
        String normalized = normalize(name);
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement("SELECT file_name FROM recordings WHERE name=?")) {
            ps.setString(1, normalized); String file = null; try (ResultSet rs = ps.executeQuery()) { if (rs.next()) file = rs.getString(1); }
            if (file == null) return false; Files.deleteIfExists(recordingsDirectory.resolve(file));
            try (PreparedStatement d = c.prepareStatement("DELETE FROM recordings WHERE name=?")) { d.setString(1, normalized); d.executeUpdate(); } return true;
        } catch (IOException | SQLException e) { throw failure("Could not delete recording " + name, e); }
    }

    public Optional<RecordingMetadata> findMetadata(String name) { try (Connection c = open()) { return findMetadata(name, c); } catch (SQLException e) { throw failure("Could not query recording " + name, e); } }
    private Optional<RecordingMetadata> findMetadata(String name, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT name,file_name,format_version,duration_ticks,frame_count,size_bytes,created_at,updated_at FROM recordings WHERE name=?")) {
            ps.setString(1, normalize(name)); try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(metadata(rs)) : Optional.empty(); }
        }
    }
    private void initialize() {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.executeUpdate("PRAGMA journal_mode = WAL");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS recordings (name TEXT PRIMARY KEY, file_name TEXT NOT NULL UNIQUE, format_version INTEGER NOT NULL, duration_ticks INTEGER NOT NULL, frame_count INTEGER NOT NULL, size_bytes INTEGER NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        } catch (SQLException e) { throw failure("Could not initialize recording index", e); }
    }
    private Connection open() throws SQLException { return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath()); }
    private static String normalize(String name) { Objects.requireNonNull(name, "name"); String value = name.trim().toLowerCase(Locale.ROOT); if (value.isBlank()) throw new IllegalArgumentException("recording name cannot be blank"); return value; }
    private static String fileNameFor(String normalized) { return UUID.nameUUIDFromBytes(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".arrec"; }
    private static void moveAtomically(Path from, Path to) throws IOException { try { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); } catch (AtomicMoveNotSupportedException e) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); } }
    private static RecordingMetadata metadata(ResultSet rs) throws SQLException { return new RecordingMetadata(rs.getString("name"), rs.getString("file_name"), rs.getInt("format_version"), rs.getLong("duration_ticks"), rs.getInt("frame_count"), rs.getLong("size_bytes"), rs.getLong("created_at"), rs.getLong("updated_at")); }
    private static RuntimeException failure(String message, Exception e) { return new IllegalStateException(message, e); }
    @Override public void close() { }
    public record RecordingMetadata(String name, String fileName, int formatVersion, long durationTicks, int frameCount, long sizeBytes, long createdAt, long updatedAt) { }
}
