package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.storage.RecordingStorageManager;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class RecordingManager {
    private final Map<String, Recording> recordings = new ConcurrentHashMap<>();
    private volatile RecordingStorageManager storage;

    public void register(Recording recording) { recordings.put(normalize(recording.name()), recording); }
    public Optional<Recording> get(String name) { return Optional.ofNullable(recordings.get(normalize(name))); }
    public boolean exists(String name) { return recordings.containsKey(normalize(name)); }
    public boolean delete(String name) { return recordings.remove(normalize(name)) != null; }
    public Collection<Recording> all() { return recordings.values().stream().toList(); }

    public void attachStorage(RecordingStorageManager storage) { this.storage = storage; }

    public CompletableFuture<Void> saveAsync(String name) {
        Recording value = get(name).orElseThrow(() -> new IllegalArgumentException("recording not found: " + name));
        RecordingStorageManager target = requireStorage();
        return target.saveAsync(value).thenApply(metadata -> null);
    }

    public CompletableFuture<Recording> loadAsync(String name) {
        RecordingStorageManager target = requireStorage();
        return target.loadAsync(name).thenApply(value -> { register(value); return value; });
    }

    public CompletableFuture<Integer> loadAllAsync() {
        return requireStorage().loadAllAsync(this);
    }

    public CompletableFuture<Boolean> deletePersistentAsync(String name) {
        RecordingStorageManager target = requireStorage();
        return target.deleteAsync(name).thenApply(deleted -> { if (deleted) delete(name); return deleted; });
    }

    public Optional<RecordingStorageManager.RecordingMetadata> metadata(String name) {
        return requireStorage().findMetadata(name);
    }

    private RecordingStorageManager requireStorage() {
        RecordingStorageManager value = storage;
        if (value == null) throw new IllegalStateException("recording storage is not attached");
        return value;
    }

    private String normalize(String name) { return name.trim().toLowerCase(java.util.Locale.ROOT); }
}
