package com.ultraop.aurareplay.ui;

import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.scene.Scene;
import com.ultraop.aurareplay.scene.SceneManager;
import com.ultraop.aurareplay.timeline.TimelineMarker;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Main-thread scene editing facade used by Studio and other presentation layers.
 * Keeps scene composition and timeline mutation out of inventory/UI code.
 */
public final class SceneStudioService {
    private final SceneManager scenes;

    public SceneStudioService(SceneManager scenes) {
        this.scenes = scenes;
    }

    public Collection<Scene> scenes() {
        return scenes.all().stream()
                .sorted(Comparator.comparing(Scene::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Scene create(String name) {
        return scenes.create(name);
    }

    public boolean delete(String name) {
        return scenes.delete(name);
    }

    public Optional<Scene> get(String name) {
        return scenes.get(name);
    }

    public boolean toggleActor(Scene scene, ActorId actorId) {
        if (scene == null || actorId == null) return false;
        if (scene.actorIds().contains(actorId)) return scenes.removeActor(scene.name(), actorId);
        return scenes.addActor(scene.name(), actorId);
    }

    public boolean assignCamera(Scene scene, String cameraId) {
        return scene != null && scenes.bindCamera(scene.name(), cameraId);
    }

    public boolean clearCamera(Scene scene) {
        return scene != null && scenes.unbindCamera(scene.name());
    }

    public long duration(Scene scene) {
        return scene == null ? 0L : scene.timeline().durationTicks();
    }

    public void setDuration(Scene scene, long ticks) {
        requireScene(scene).timeline().setDurationTicks(Math.max(0L, ticks));
    }

    public void setRange(Scene scene, long in, long out) {
        requireScene(scene).timeline().setRange(in, out);
    }

    public void setSpeed(Scene scene, double speed) {
        requireScene(scene).timeline().setPlaybackSpeed(speed);
    }

    public void toggleLoop(Scene scene) {
        Scene value = requireScene(scene);
        value.timeline().setLoop(!value.timeline().loop());
    }

    public void toggleReverse(Scene scene) {
        Scene value = requireScene(scene);
        value.timeline().setReverse(!value.timeline().reverse());
    }

    public List<TimelineMarker> markers(Scene scene) {
        return requireScene(scene).timeline().markers();
    }

    public void addMarker(Scene scene, TimelineMarker marker) {
        requireScene(scene).timeline().addMarker(marker);
    }

    public boolean removeMarker(Scene scene, String id) {
        return requireScene(scene).timeline().removeMarker(id);
    }

    private static Scene requireScene(Scene scene) {
        if (scene == null) throw new IllegalArgumentException("scene cannot be null");
        return scene;
    }
}
