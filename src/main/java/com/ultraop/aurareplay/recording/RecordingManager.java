package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.actor.ActorPlaybackSource;
import com.ultraop.aurareplay.storage.RecordingStorageManager;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class RecordingManager {
    private final Map<String, Recording> recordings = new ConcurrentHashMap<>();
    private volatile RecordingStorageManager storage;
    private volatile Consumer<String> deletionListener = name -> { };

    public void register(Recording recording) { recordings.put(normalize(recording.name()), recording); }
    public Optional<Recording> get(String name) { return Optional.ofNullable(recordings.get(normalize(name))); }
    public boolean exists(String name) { return recordings.containsKey(normalize(name)); }
    public boolean delete(String name) { return recordings.remove(normalize(name)) != null; }
    public Collection<Recording> all() { return recordings.values().stream().toList(); }
    public void attachStorage(RecordingStorageManager storage) { this.storage = storage; }

    /** Called after a persistent recording deletion succeeds. The listener owns dependent-resource cleanup. */
    public void setDeletionListener(Consumer<String> listener) {
        deletionListener = listener == null ? name -> { } : listener;
    }

    public CompletableFuture<Void> saveAsync(String name) {
        Recording value = get(name).orElseThrow(() -> new IllegalArgumentException("recording not found: " + name));
        return requireStorage().saveAsync(value).thenApply(metadata -> null);
    }

    public CompletableFuture<Recording> loadAsync(String name) {
        return requireStorage().loadAsync(name).thenApply(value -> { register(value); return value; });
    }

    public CompletableFuture<Integer> loadAllAsync() { return requireStorage().loadAllAsync(this); }

    public CompletableFuture<Boolean> deletePersistentAsync(String name) {
        String normalized = normalize(name);
        return requireStorage().deleteAsync(normalized).thenApply(deleted -> {
            if (deleted) {
                delete(normalized);
                deletionListener.accept(normalized);
            }
            return deleted;
        });
    }

    public Optional<RecordingStorageManager.RecordingMetadata> metadata(String name) { return requireStorage().findMetadata(name); }

    /** Builds the disk-backed playback source off the server thread. */
    public CompletableFuture<ActorPlaybackSource> createPlaybackSourceAsync(String name) {
        RecordingStorageManager target = requireStorage();
        return CompletableFuture.supplyAsync(() -> {
            try { return target.createPlaybackSource(name); }
            catch (Exception e) { throw new IllegalStateException("Could not create playback source for " + name, e); }
        }, target.executor());
    }

    private RecordingStorageManager requireStorage() {
        RecordingStorageManager value = storage;
        if (value == null) throw new IllegalStateException("recording storage is not attached");
        return value;
    }

    private String normalize(String name) { return name.trim().toLowerCase(java.util.Locale.ROOT); }
}
