package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraController;
import com.ultraop.aurareplay.camera.CameraDefinition;
import com.ultraop.aurareplay.camera.CameraManager;
import com.ultraop.aurareplay.scene.Scene;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Coordinates scene director plans with viewer-local cinematic camera playback. */
public final class DirectorController {
    private final CameraManager cameras;
    private final CameraController cameraController;
    private final Map<String, DirectorPlan> plans = new ConcurrentHashMap<>();

    public DirectorController(CameraManager cameras, CameraController cameraController) {
        this.cameras = Objects.requireNonNull(cameras, "cameras");
        this.cameraController = Objects.requireNonNull(cameraController, "cameraController");
    }

    public DirectorPlan plan(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        return plans.computeIfAbsent(scene.name(), ignored -> new DirectorPlan());
    }

    public void setPlan(Scene scene, DirectorPlan plan) {
        Objects.requireNonNull(scene, "scene");
        plans.put(scene.name(), Objects.requireNonNull(plan, "plan"));
    }

    public boolean start(Player viewer, Scene scene, double tick) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(scene, "scene");
        DirectorShot shot = plan(scene).at(tick).orElse(null);
        if (shot == null) return false;
        CameraDefinition camera = cameras.get(shot.cameraId()).orElse(null);
        if (camera == null) return false;
        cameraController.start(viewer, camera, Math.max(0.0, tick - shot.startTick()));
        return true;
    }

    /** Selects the active shot at a scene tick and seeks its camera to the shot-local tick. */
    public boolean renderAt(Player viewer, Scene scene, double tick) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(scene, "scene");
        DirectorShot shot = plan(scene).at(tick).orElse(null);
        if (shot == null) return false;
        CameraDefinition camera = cameras.get(shot.cameraId()).orElse(null);
        if (camera == null) return false;
        double localTick = Math.max(0.0, tick - shot.startTick());
        if (!cameraController.active(viewer)) cameraController.start(viewer, camera, localTick);
        else cameraController.seek(viewer, localTick);
        return true;
    }

    public void stop(Player viewer) { cameraController.stop(viewer); }
    public boolean active(Player viewer) { return cameraController.active(viewer); }
    public int planCount() { return plans.size(); }
    public void clear() { plans.clear(); }
}
