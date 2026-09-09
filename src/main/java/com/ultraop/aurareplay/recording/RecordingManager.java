package com.ultraop.aurareplay.recording;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RecordingManager {

    private final Map<String, Recording> recordings = new ConcurrentHashMap<>();

    public void register(Recording recording) {
        recordings.put(normalize(recording.name()), recording);
    }

    public Optional<Recording> get(String name) {
        return Optional.ofNullable(recordings.get(normalize(name)));
    }

    public boolean delete(String name) {
        return recordings.remove(normalize(name)) != null;
    }

    public Collection<Recording> all() {
        return recordings.values().stream().toList();
    }

    private String normalize(String name) {
        return name.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
