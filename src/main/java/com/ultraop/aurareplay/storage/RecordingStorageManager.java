package com.ultraop.aurareplay.storage;

import com.ultraop.aurareplay.actor.ActorPlaybackSource;
import com.ultraop.aurareplay.actor.IndexedActorPlaybackSource;
import com.ultraop.aurareplay.recording.Recording;
import com.ultraop.aurareplay.recording.RecordingBinaryCodec;
import com.ultraop.aurareplay.recording.RecordingIndexedFile;
import com.ultraop.aurareplay.recording.RecordingManager;
import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class RecordingStorageManager implements AutoCloseable {
    private static final int LEGACY_FORMAT_VERSION = 3;
    private static final int FORMAT_VERSION = 4;
    private final File databaseFile;
    private final Path recordingsDirectory;
    private final Path corruptDirectory;
    private final RecordingBinaryCodec legacyCodec;
    private final Executor executor;

    public RecordingStorageManager(JavaPlugin plugin, Executor executor) {
        Objects.requireNonNull(plugin, "plugin");
        this.executor = Objects.requireNonNull(executor, "executor");
        File data = plugin.getDataFolder();
        if (!data.exists() && !data.mkdirs()) throw new IllegalStateException("Could not create plugin data directory");
        databaseFile = new File(data, "aurareplay.db");
        recordingsDirectory = new File(data, "recordings").toPath();
        corruptDirectory = recordingsDirectory.resolve("corrupt");
        try {
            Files.createDirectories(recordingsDirectory);
            Files.createDirectories(corruptDirectory);
            cleanupTemporaryFiles();
        } catch (IOException e) {
            throw new IllegalStateException("Could not create recordings directories", e);
        }
        legacyCodec = new RecordingBinaryCodec();
        initialize();
    }

    public Executor executor() { return executor; }
    public CompletableFuture<List<RecordingMetadata>> listAsync() { return CompletableFuture.supplyAsync(this::list, executor); }

    public List<RecordingMetadata> list() {
        List<RecordingMetadata> result = new ArrayList<>();
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement("SELECT name,file_name,format_version,duration_ticks,frame_count,size_bytes,created_at,updated_at,sha256 FROM recordings ORDER BY name"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(metadata(rs));
            return List.copyOf(result);
        } catch (SQLException e) { throw failure("Could not list recordings", e); }
    }

    public CompletableFuture<Integer> loadAllAsync(RecordingManager manager) {
        Objects.requireNonNull(manager, "manager");
        return CompletableFuture.supplyAsync(() -> {
            int loaded = 0;
            for (RecordingMetadata m : list()) {
                try { manager.register(load(m)); loaded++; }
                catch (RuntimeException | IOException ignored) { }
            }
            return loaded;
        }, executor);
    }

    public CompletableFuture<Recording> loadAsync(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try { return load(name); }
            catch (IOException e) { throw failure("Could not load recording " + name, e); }
        }, executor);
    }

    public Recording load(String name) throws IOException { return load(findMetadata(name).orElseThrow(() -> new IOException("recording not found: " + name))); }

    private Recording load(RecordingMetadata m) throws IOException {
        Path p = resolveRecordingPath(m);
        verifyChecksum(m, p);
        Recording r;
        if (m.formatVersion() == FORMAT_VERSION) r = RecordingIndexedFile.read(p);
        else if (m.formatVersion() == LEGACY_FORMAT_VERSION) r = legacyCodec.decode(Files.readAllBytes(p));
        else throw new IOException("unsupported stored recording format version: " + m.formatVersion());
        validateMetadata(m, r);
        return r;
    }

    public ActorPlaybackSource createPlaybackSource(String name) throws IOException { return createPlaybackSource(name, IndexedActorPlaybackSource.DEFAULT_CACHE_CAPACITY); }

    public ActorPlaybackSource createPlaybackSource(String name, int cacheCapacity) throws IOException {
        RecordingMetadata m = findMetadata(name).orElseThrow(() -> new IOException("recording not found: " + name));
        if (m.formatVersion() != FORMAT_VERSION) throw new IOException("indexed playback requires recording format version " + FORMAT_VERSION);
        Path path = resolveRecordingPath(m);
        verifyChecksum(m, path);
        return new IndexedActorPlaybackSource(path, m.frameCount(), cacheCapacity);
    }

    public TickLookup readFrame(String name, int frameIndex) throws IOException {
        RecordingMetadata m = findMetadata(name).orElseThrow(() -> new IOException("recording not found: " + name));
        if (m.formatVersion() != FORMAT_VERSION) throw new IOException("random access requires indexed recording format");
        Path path = resolveRecordingPath(m);
        verifyChecksum(m, path);
        return new TickLookup(m.name(), frameIndex, RecordingIndexedFile.readFrame(path, frameIndex));
    }

    public CompletableFuture<RecordingMetadata> saveAsync(Recording recording) { return CompletableFuture.supplyAsync(() -> save(recording), executor); }

    public RecordingMetadata save(Recording recording) {
        Objects.requireNonNull(recording, "recording");
        String n = normalize(recording.name()), f = fileNameFor(n);
        Path target = recordingsDirectory.resolve(f), temp = recordingsDirectory.resolve(f + ".tmp");
        try {
            RecordingIndexedFile.write(temp, recording);
            moveAtomically(temp, target);
            String checksum = RecordingIntegrity.sha256(target);
            long now = Instant.now().toEpochMilli();
            try (Connection c = open(); PreparedStatement ps = c.prepareStatement("INSERT INTO recordings(name,file_name,format_version,duration_ticks,frame_count,size_bytes,created_at,updated_at,sha256) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(name) DO UPDATE SET file_name=excluded.file_name,format_version=excluded.format_version,duration_ticks=excluded.duration_ticks,frame_count=excluded.frame_count,size_bytes=excluded.size_bytes,updated_at=excluded.updated_at,sha256=excluded.sha256")) {
                Optional<RecordingMetadata> old = findMetadata(n, c);
                ps.setString(1, n); ps.setString(2, f); ps.setInt(3, FORMAT_VERSION); ps.setLong(4, recording.durationTicks()); ps.setInt(5, recording.frames().size()); ps.setLong(6, Files.size(target)); ps.setLong(7, old.map(RecordingMetadata::createdAt).orElse(now)); ps.setLong(8, now); ps.setString(9, checksum); ps.executeUpdate();
            }
            return findMetadata(n).orElseThrow(() -> new IllegalStateException("recording index write failed"));
        } catch (IOException | SQLException e) {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
            throw failure("Could not save recording " + recording.name(), e);
        }
    }

    public CompletableFuture<Boolean> deleteAsync(String name) { return CompletableFuture.supplyAsync(() -> delete(name), executor); }

    public boolean delete(String name) {
        String n = normalize(name);
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement("SELECT file_name FROM recordings WHERE name=?")) {
            ps.setString(1, n); String file = null;
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) file = rs.getString(1); }
            if (file == null) return false;
            Files.deleteIfExists(recordingsDirectory.resolve(file));
            try (PreparedStatement d = c.prepareStatement("DELETE FROM recordings WHERE name=?")) { d.setString(1, n); d.executeUpdate(); }
            return true;
        } catch (IOException | SQLException e) { throw failure("Could not delete recording " + name, e); }
    }

    /** Validates every indexed recording and quarantines files that fail structural or checksum validation. */
    public CompletableFuture<List<IntegrityResult>> validateStoredRecordingsAsync() {
        return CompletableFuture.supplyAsync(this::validateStoredRecordings, executor);
    }

    public List<IntegrityResult> validateStoredRecordings() {
        List<IntegrityResult> results = new ArrayList<>();
        for (RecordingMetadata metadata : list()) {
            try {
                Recording recording = load(metadata);
                results.add(new IntegrityResult(metadata.name(), true, "ok"));
                if (recording.frames().size() != metadata.frameCount()) throw new IOException("frame count mismatch");
            } catch (Exception e) {
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                results.add(new IntegrityResult(metadata.name(), false, reason));
                quarantine(metadata, reason);
            }
        }
        return List.copyOf(results);
    }

    private void quarantine(RecordingMetadata metadata, String reason) {
        try {
            Path source = recordingsDirectory.resolve(metadata.fileName()).normalize();
            if (!Files.isRegularFile(source)) return;
            String suffix = ".corrupt-" + Instant.now().toEpochMilli();
            Path destination = corruptDirectory.resolve(metadata.fileName() + suffix).normalize();
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) { }
    }

    public Optional<RecordingMetadata> findMetadata(String name) {
        try (Connection c = open()) { return findMetadata(name, c); }
        catch (SQLException e) { throw failure("Could not query recording " + name, e); }
    }

    private Optional<RecordingMetadata> findMetadata(String name, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT name,file_name,format_version,duration_ticks,frame_count,size_bytes,created_at,updated_at,sha256 FROM recordings WHERE name=?")) {
            ps.setString(1, normalize(name));
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(metadata(rs)) : Optional.empty(); }
        }
    }

    private Path resolveRecordingPath(RecordingMetadata m) throws IOException {
        Path p = recordingsDirectory.resolve(m.fileName()).normalize();
        if (!p.getParent().equals(recordingsDirectory) || !Files.isRegularFile(p)) throw new IOException("recording file missing: " + p.getFileName());
        return p;
    }

    private void verifyChecksum(RecordingMetadata m, Path path) throws IOException {
        if (m.sha256() == null || m.sha256().isBlank()) return; // pre-integrity metadata is accepted and upgraded on next save
        String actual = RecordingIntegrity.sha256(path);
        if (!m.sha256().equalsIgnoreCase(actual)) throw new IOException("recording checksum mismatch: " + m.name());
    }

    private void validateMetadata(RecordingMetadata m, Recording r) throws IOException {
        if (!normalize(r.name()).equals(m.name())) throw new IOException("recording metadata/name mismatch: " + m.name());
        if (r.durationTicks() != m.durationTicks()) throw new IOException("recording metadata/duration mismatch: " + m.name());
        if (r.frames().size() != m.frameCount()) throw new IOException("recording metadata/frame-count mismatch: " + m.name());
    }

    private void initialize() {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.executeUpdate("PRAGMA journal_mode = WAL");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS recordings (name TEXT PRIMARY KEY, file_name TEXT NOT NULL UNIQUE, format_version INTEGER NOT NULL, duration_ticks INTEGER NOT NULL, frame_count INTEGER NOT NULL, size_bytes INTEGER NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, sha256 TEXT)");
            try { s.executeUpdate("ALTER TABLE recordings ADD COLUMN sha256 TEXT"); } catch (SQLException ignored) { }
        } catch (SQLException e) { throw failure("Could not initialize recording index", e); }
    }

    private void cleanupTemporaryFiles() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(recordingsDirectory, "*.arrec.tmp")) {
            for (Path temp : stream) Files.deleteIfExists(temp);
        }
    }

    private Connection open() throws SQLException { return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath()); }
    private static String normalize(String name) { Objects.requireNonNull(name, "name"); String v = name.trim().toLowerCase(Locale.ROOT); if (v.isBlank()) throw new IllegalArgumentException("recording name cannot be blank"); return v; }
    private static String fileNameFor(String normalized) { return UUID.nameUUIDFromBytes(normalized.getBytes(StandardCharsets.UTF_8)) + ".arrec"; }
    private static void moveAtomically(Path from, Path to) throws IOException { try { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); } catch (AtomicMoveNotSupportedException e) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); } }
    private static RecordingMetadata metadata(ResultSet rs) throws SQLException { return new RecordingMetadata(rs.getString("name"), rs.getString("file_name"), rs.getInt("format_version"), rs.getLong("duration_ticks"), rs.getInt("frame_count"), rs.getLong("size_bytes"), rs.getLong("created_at"), rs.getLong("updated_at"), rs.getString("sha256")); }
    private static RuntimeException failure(String message, Exception e) { return new IllegalStateException(message, e); }
    @Override public void close() { }

    public record RecordingMetadata(String name, String fileName, int formatVersion, long durationTicks, int frameCount, long sizeBytes, long createdAt, long updatedAt, String sha256) { }
    public record TickLookup(String recordingName, int frameIndex, TickSnapshot frame) { }
    public record IntegrityResult(String recordingName, boolean valid, String message) { }
}
