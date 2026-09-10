package com.ultraop.aurareplay.scene;

import com.ultraop.aurareplay.effects.EffectPlaybackCursor;
import com.ultraop.aurareplay.timeline.TimelineCursor;

import java.util.Objects;

/** Viewer-local scene playback state. Scene time is authoritative for synchronized actors. */
public final class ScenePlaybackSession {
    private final Scene scene;
    private final TimelineCursor cursor = new TimelineCursor();
    private final EffectPlaybackCursor effectCursor = new EffectPlaybackCursor();
    private boolean finished;
    private boolean paused;

    public ScenePlaybackSession(Scene scene) {
        this.scene = Objects.requireNonNull(scene, "scene");
        cursor.reset(scene.timeline());
        effectCursor.reset(cursor.tick());
    }

    public Scene scene() { return scene; }
    public TimelineCursor cursor() { return cursor; }
    public EffectPlaybackCursor effectCursor() { return effectCursor; }
    public boolean finished() { return finished; }
    public boolean paused() { return paused; }
    public void setPaused(boolean paused) { if (!finished) this.paused = paused; }
    public void seek(double tick) { cursor.setTick(tick, scene.timeline()); effectCursor.seek(cursor.tick()); finished = false; }
    public void resetEffectsAtCurrentTick() { effectCursor.reset(cursor.tick()); }

    /** Advances scene time by one server tick and reports whether playback remains active. */
    public boolean tick() {
        if (finished) return false;
        if (paused) return true;
        boolean active = cursor.advance(scene.timeline());
        if (!scene.timeline().loop()) {
            boolean atEnd = scene.timeline().reverse()
                    ? cursor.tick() <= scene.timeline().inPoint()
                    : cursor.tick() >= scene.timeline().outPoint();
            if (atEnd) finished = true;
        }
        return active;
    }
}
