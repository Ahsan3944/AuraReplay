package com.ultraop.aurareplay.scene;

import com.ultraop.aurareplay.timeline.TimelineCursor;

import java.util.Objects;

/** Viewer-local scene playback state. Scene time is authoritative for synchronized actors. */
public final class ScenePlaybackSession {
    private final Scene scene;
    private final TimelineCursor cursor = new TimelineCursor();
    private boolean finished;

    public ScenePlaybackSession(Scene scene) {
        this.scene = Objects.requireNonNull(scene, "scene");
        cursor.reset(scene.timeline());
    }

    public Scene scene() { return scene; }
    public TimelineCursor cursor() { return cursor; }
    public boolean finished() { return finished; }

    /** Advances scene time by one server tick and reports whether playback remains active. */
    public boolean tick() {
        if (finished) return false;
        boolean active = cursor.advance(scene.timeline());
        if (!scene.timeline().loop() && cursor.tick() == scene.timeline().outPoint()) {
            finished = true;
        }
        return active;
    }
}
