package com.ultraop.aurareplay.scene;

import com.ultraop.aurareplay.camera.CameraDefinition;
import com.ultraop.aurareplay.camera.CameraManager;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Associates a scene with an optional cinematic camera without coupling scene data to rendering. */
public final class SceneCameraService {
    private final CameraManager cameras;
    private final Map<String, String> bindings = new ConcurrentHashMap<>();

    public SceneCameraService(CameraManager cameras) { this.cameras = cameras; }

    public boolean bind(Scene scene, String cameraId) {
        if (scene == null || cameraId == null || cameras.get(cameraId).isEmpty()) return false;
        bindings.put(key(scene), cameraId);
        return true;
    }

    public boolean unbind(Scene scene) { return scene != null && bindings.remove(key(scene)) != null; }

    public Optional<CameraDefinition> camera(Scene scene) {
        if (scene == null) return Optional.empty();
        String id = bindings.get(key(scene));
        return id == null ? Optional.empty() : cameras.get(id);
    }

    public Optional<String> cameraId(Scene scene) {
        if (scene == null) return Optional.empty();
        return Optional.ofNullable(bindings.get(key(scene)));
    }

    public void clear() { bindings.clear(); }
    private static String key(Scene scene) { return scene.name().trim().toLowerCase(java.util.Locale.ROOT); }
}
