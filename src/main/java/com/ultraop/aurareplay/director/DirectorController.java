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
    private final Map<String, Scene> scenes = new ConcurrentHashMap<>();
    private final Map<UUID, PlaybackState> playback = new ConcurrentHashMap<>();

    public DirectorController(CameraManager cameras, CameraController cameraController, ActorPlaybackController actorPlayback) {
        this.cameras = Objects.requireNonNull(cameras, "cameras");
        this.cameraController = Objects.requireNonNull(cameraController, "cameraController");
        this.actorPlayback = Objects.requireNonNull(actorPlayback, "actorPlayback");
    }

    public DirectorPlan plan(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        scenes.put(scene.name(), scene);
        return plans.computeIfAbsent(scene.name(), ignored -> new DirectorPlan());
    }

    public void setPlan(Scene scene, DirectorPlan plan) {
        Objects.requireNonNull(scene, "scene");
        scenes.put(scene.name(), scene);
        plans.put(scene.name(), Objects.requireNonNull(plan, "plan"));
    }

    /** Starts viewer-local director playback at the supplied scene tick. */
    public boolean start(Player viewer, Scene scene, double tick) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(scene, "scene");
        if (!Double.isFinite(tick) || tick < 0.0 || plan(scene).at(tick).isEmpty()) return false;
        PlaybackState state = new PlaybackState(scene.name(), tick, true);
        playback.put(viewer.getUniqueId(), state);
        if (!renderAt(viewer, scene, tick)) {
            playback.remove(viewer.getUniqueId());
            cameraController.stop(viewer);
            return false;
        }
        return true;
    }

    /** Resumes playback from the viewer's current director cursor. */
    public boolean play(Player viewer) {
        PlaybackState state = playback.get(viewer.getUniqueId());
        if (state == null) return false;
        state.playing = true;
        return true;
    }

    /** Pauses playback while retaining the current camera and director cursor. */
    public boolean pause(Player viewer) {
        PlaybackState state = playback.get(viewer.getUniqueId());
        if (state == null) return false;
        state.playing = false;
        return true;
    }

    /** Seeks the active director session and immediately renders the requested tick. */
    public boolean seek(Player viewer, double tick) {
        if (!Double.isFinite(tick) || tick < 0.0) return false;
        PlaybackState state = playback.get(viewer.getUniqueId());
        if (state == null) return false;
        Scene scene = scenes.get(state.sceneName);
        if (scene == null || plan(scene).at(tick).isEmpty()) return false;
        if (!renderAt(viewer, scene, tick)) return false;
        state.tick = tick;
        return true;
    }

    /** Advances every playing director session by one Minecraft tick. */
    public void tick(Player viewer) {
        PlaybackState state = playback.get(viewer.getUniqueId());
        if (state == null || !state.playing) return;
        DirectorPlan directorPlan = plans.get(state.sceneName);
        Scene scene = scenes.get(state.sceneName);
        if (directorPlan == null || scene == null) {
            stop(viewer);
            return;
        }
        double nextTick = state.tick + 1.0;
        if (nextTick >= directorPlan.durationTicks() || directorPlan.at(nextTick).isEmpty()) {
            stop(viewer);
            return;
        }
        if (!renderAt(viewer, scene, nextTick)) {
            stop(viewer);
            return;
        }
        state.tick = nextTick;
    }

    /** Evaluates the active shot, path, target and shot-to-shot transition using viewer-local state. */
    public boolean renderAt(Player viewer, Scene scene, double tick) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(scene, "scene");
        DirectorPlan directorPlan = plan(scene);
        DirectorShot shot = directorPlan.at(tick).orElse(null);
        if (shot == null) return false;
        CameraDefinition camera = cameras.get(shot.cameraId()).orElse(null);
        if (camera == null) return false;
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
        CameraDefinition camera = cameras.get(shot.cameraId()).orElse(null);
        if (camera == null) return null;
        double localTick = Math.max(0.0, shot.durationTicks() - 1.0);
        return targetTransform(viewer, shot, pathTransform(shot, camera.sample(localTick), localTick));
    }

    private CameraTransform pathTransform(DirectorShot shot, CameraTransform base, double localTick) {
        return shot.path().sample(shot.type(), localTick, base);
    }

    private CameraTransform targetTransform(Player viewer, DirectorShot shot, CameraTransform base) {
        ActorSample target = resolveTarget(viewer, shot);
        if (target == null || !target.exists()) return base;
        double x = target.transform().x(), y = target.transform().y(), z = target.transform().z();
        return switch (shot.type()) {
            case FOLLOW -> {
                CameraTransform origin = cameras.get(shot.cameraId()).map(c -> c.sample(0.0)).orElse(base);
                yield CameraTargetMath.follow(base, x, y, z, origin.x() - x, origin.y() - y, origin.z() - z);
            }
            case LOOK_AT, ORBIT -> CameraTargetMath.lookAt(base, x, y, z);
            default -> base;
        };
    }

    private ActorSample resolveTarget(Player viewer, DirectorShot shot) {
        if (shot.targetActorId() == null || shot.targetActorId().isBlank()) return null;
        try {
            return actorPlayback.sample(viewer, new ActorId(UUID.fromString(shot.targetActorId())));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void stop(Player viewer) {
        playback.remove(viewer.getUniqueId());
        cameraController.stop(viewer);
    }

    public void stopAll(Iterable<? extends Player> viewers) {
        viewers.forEach(this::stop);
    }

    public boolean active(Player viewer) {
        return playback.containsKey(viewer.getUniqueId());
    }

    public boolean playing(Player viewer) {
        PlaybackState state = playback.get(viewer.getUniqueId());
        return state != null && state.playing;
    }

    public double currentTick(Player viewer) {
        PlaybackState state = playback.get(viewer.getUniqueId());
        return state == null ? 0.0 : state.tick;
    }

    public String currentScene(Player viewer) {
        PlaybackState state = playback.get(viewer.getUniqueId());
        return state == null ? null : state.sceneName;
    }

    public int planCount() { return plans.size(); }

    public void clear() {
        playback.clear();
        scenes.clear();
        plans.clear();
    }

    private static final class PlaybackState {
        private final String sceneName;
        private volatile double tick;
        private volatile boolean playing;

        private PlaybackState(String sceneName, double tick, boolean playing) {
            this.sceneName = sceneName;
            this.tick = tick;
            this.playing = playing;
        }
    }
}
