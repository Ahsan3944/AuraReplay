package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorSample;
import com.ultraop.aurareplay.actor.ActorPlaybackController;
import com.ultraop.aurareplay.camera.CameraController;
import com.ultraop.aurareplay.camera.CameraDefinition;
import com.ultraop.aurareplay.camera.CameraManager;
import com.ultraop.aurareplay.camera.CameraTransform;
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
    private final ActorPlaybackController actorPlayback;
    private final Map<String, DirectorPlan> plans = new ConcurrentHashMap<>();

    public DirectorController(CameraManager cameras, CameraController cameraController, ActorPlaybackController actorPlayback) {
        this.cameras = Objects.requireNonNull(cameras, "cameras");
        this.cameraController = Objects.requireNonNull(cameraController, "cameraController");
        this.actorPlayback = Objects.requireNonNull(actorPlayback, "actorPlayback");
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
        return renderAt(viewer, scene, tick);
    }

    /** Selects the active shot, evaluates its target, and applies only a viewer-local camera transform. */
    public boolean renderAt(Player viewer, Scene scene, double tick) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(scene, "scene");
        DirectorShot shot = plan(scene).at(tick).orElse(null);
        if (shot == null) return false;
        CameraDefinition camera = cameras.get(shot.cameraId()).orElse(null);
        if (camera == null) return false;

        double localTick = Math.max(0.0, tick - shot.startTick());
        CameraDefinition activeCamera = cameraController.currentCamera(viewer);
        if (activeCamera != camera) {
            cameraController.start(viewer, camera, localTick);
        } else {
            cameraController.seek(viewer, localTick);
        }

        CameraTransform transform = camera.sample(localTick);
        ActorSample target = resolveTarget(viewer, shot);
        if (target != null && target.exists()) {
            double targetX = target.transform().x();
            double targetY = target.transform().y();
            double targetZ = target.transform().z();
            switch (shot.type()) {
                case FOLLOW -> transform = CameraTargetMath.follow(transform, targetX, targetY, targetZ, 
                        targetX - camera.sample(0.0).x(), targetY - camera.sample(0.0).y(), targetZ - camera.sample(0.0).z());
                case LOOK_AT, ORBIT -> transform = CameraTargetMath.lookAt(transform, targetX, targetY, targetZ);
                default -> { }
            }
        }
        return cameraController.overrideTransform(viewer, transform);
    }

    private ActorSample resolveTarget(Player viewer, DirectorShot shot) {
        if (shot.targetActorId() == null || shot.targetActorId().isBlank()) return null;
        try {
            return actorPlayback.sample(viewer, new ActorId(UUID.fromString(shot.targetActorId())));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void stop(Player viewer) { cameraController.stop(viewer); }
    public boolean active(Player viewer) { return cameraController.active(viewer); }
    public int planCount() { return plans.size(); }
    public void clear() { plans.clear(); }
}
