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
    public DirectorPlan plan(Scene scene) { Objects.requireNonNull(scene, "scene"); return plans.computeIfAbsent(scene.name(), ignored -> new DirectorPlan()); }
    public void setPlan(Scene scene, DirectorPlan plan) { Objects.requireNonNull(scene, "scene"); plans.put(scene.name(), Objects.requireNonNull(plan, "plan")); }
    public boolean start(Player viewer, Scene scene, double tick) { return renderAt(viewer, scene, tick); }

    /** Evaluates the active shot, path, target and shot-to-shot transition using viewer-local state. */
    public boolean renderAt(Player viewer, Scene scene, double tick) {
        Objects.requireNonNull(viewer, "viewer"); Objects.requireNonNull(scene, "scene");
        DirectorPlan directorPlan = plan(scene);
        DirectorShot shot = directorPlan.at(tick).orElse(null); if (shot == null) return false;
        CameraDefinition camera = cameras.get(shot.cameraId()).orElse(null); if (camera == null) return false;
        double localTick = Math.max(0.0, tick - shot.startTick());
        CameraDefinition activeCamera = cameraController.currentCamera(viewer);
        if (activeCamera == null || !activeCamera.id().equals(camera.id())) cameraController.start(viewer, camera, localTick);
        else cameraController.seek(viewer, localTick);
        CameraTransform currentTransform = targetTransform(viewer, shot, pathTransform(shot, camera.sample(localTick), localTick));
        DirectorShot previous = directorPlan.previousBefore(shot.startTick()).orElse(null);
        CameraTransform previousTransform = previous == null ? null : previousTransform(viewer, previous);
        DirectorTransitionEvaluator.Result transition = DirectorTransitionEvaluator.evaluate(shot, previous, currentTransform, previousTransform, tick);
        return cameraController.overrideTransform(viewer, transition.transform());
    }

    private CameraTransform previousTransform(Player viewer, DirectorShot shot) {
        CameraDefinition camera = cameras.get(shot.cameraId()).orElse(null); if (camera == null) return null;
        double localTick = Math.max(0.0, shot.durationTicks() - 1.0);
        return targetTransform(viewer, shot, pathTransform(shot, camera.sample(localTick), localTick));
    }
    private CameraTransform pathTransform(DirectorShot shot, CameraTransform base, double localTick) { return shot.path().sample(shot.type(), localTick, base); }
    private CameraTransform targetTransform(Player viewer, DirectorShot shot, CameraTransform base) {
        ActorSample target = resolveTarget(viewer, shot); if (target == null || !target.exists()) return base;
        double x=target.transform().x(), y=target.transform().y(), z=target.transform().z();
        return switch (shot.type()) {
            case FOLLOW -> { CameraTransform origin=cameras.get(shot.cameraId()).map(c->c.sample(0.0)).orElse(base); yield CameraTargetMath.follow(base,x,y,z,origin.x()-x,origin.y()-y,origin.z()-z); }
            case LOOK_AT, ORBIT -> CameraTargetMath.lookAt(base,x,y,z);
            default -> base;
        };
    }
    private ActorSample resolveTarget(Player viewer, DirectorShot shot) {
        if (shot.targetActorId()==null || shot.targetActorId().isBlank()) return null;
        try { return actorPlayback.sample(viewer,new ActorId(UUID.fromString(shot.targetActorId()))); } catch (IllegalArgumentException ignored) { return null; }
    }
    public void stop(Player viewer) { cameraController.stop(viewer); }
    public boolean active(Player viewer) { return cameraController.active(viewer); }
    public int planCount() { return plans.size(); }
    public void clear() { plans.clear(); }
}
