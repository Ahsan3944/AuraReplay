package com.ultraop.aurareplay.camera;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Registry for reusable cinematic cameras. */
public final class CameraManager {
    private final Map<String, CameraDefinition> cameras = new ConcurrentHashMap<>();

    public CameraDefinition create(String id, String name, CameraTransform transform) {
        String key = normalize(id);
        if (cameras.containsKey(key)) throw new IllegalArgumentException("camera already exists: " + id);
        CameraDefinition camera = new CameraDefinition(id, name, transform);
        cameras.put(key, camera);
        return camera;
    }

    public Optional<CameraDefinition> get(String id) { return Optional.ofNullable(cameras.get(normalize(id))); }
    public boolean remove(String id) { return cameras.remove(normalize(id)) != null; }
    public Collection<CameraDefinition> all() { return cameras.values().stream().toList(); }
    public void clear() { cameras.clear(); }

    private static String normalize(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("camera id cannot be empty");
        return id.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
