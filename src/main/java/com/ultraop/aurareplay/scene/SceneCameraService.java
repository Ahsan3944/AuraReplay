package com.ultraop.aurareplay.scene;

import com.ultraop.aurareplay.camera.CameraDefinition;
import com.ultraop.aurareplay.camera.CameraManager;

import java.util.Objects;
import java.util.Optional;

/** Associates a scene with an optional cinematic camera without coupling scene data to rendering. */
public final class SceneCameraService {
    private final CameraManager cameras;

    public SceneCameraService(CameraManager cameras) {
        this.cameras = Objects.requireNonNull(cameras, "cameras");
    }

    public boolean bind(Scene scene, String cameraId) {
        if (scene == null || cameraId == null || cameras.get(cameraId).isEmpty()) return false;
        scene.setCameraId(cameraId);
        return true;
    }

    public boolean unbind(Scene scene) {
        if (scene == null || scene.cameraId() == null) return false;
        scene.setCameraId(null);
        return true;
    }

    public Optional<CameraDefinition> camera(Scene scene) {
        if (scene == null || scene.cameraId() == null) return Optional.empty();
        return cameras.get(scene.cameraId());
    }

    public Optional<String> cameraId(Scene scene) {
        return scene == null ? Optional.empty() : Optional.ofNullable(scene.cameraId());
    }

    public void clear() {
        // Camera bindings are owned by Scene objects; there is no secondary registry to clear.
    }
}
