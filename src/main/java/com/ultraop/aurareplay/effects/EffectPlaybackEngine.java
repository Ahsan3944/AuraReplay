package com.ultraop.aurareplay.effects;

import com.ultraop.aurareplay.scene.Scene;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Deterministic, viewer-local traversal of scene effect cues. */
public final class EffectPlaybackEngine {
    public void reset(EffectPlaybackCursor cursor, double tick) { Objects.requireNonNull(cursor, "cursor").reset(tick); }

    public void seek(EffectPlaybackCursor cursor, double tick) { Objects.requireNonNull(cursor, "cursor").seek(tick); }

    /** Fires every cue crossed between the previous and current scene ticks. */
    public int advance(Player viewer, Scene scene, EffectPlaybackCursor cursor, double currentTick, boolean reverse) {
        Objects.requireNonNull(viewer, "viewer"); Objects.requireNonNull(scene, "scene"); Objects.requireNonNull(cursor, "cursor");
        if (!Double.isFinite(currentTick)) throw new IllegalArgumentException("currentTick must be finite");
        if (!cursor.initialized()) { cursor.reset(currentTick); return 0; }
        double from = cursor.previousTick();
        if (Math.abs(currentTick - from) < 1.0e-9d) return 0;
        List<EffectCue> cues = new ArrayList<>();
        if (reverse) {
            for (EffectCue cue : scene.effects().all()) if (cursor.crossedReverse(cue.tick(), from, currentTick)) cues.add(cue);
            cues.sort(Comparator.comparingLong(EffectCue::tick).reversed());
        } else {
            for (EffectCue cue : scene.effects().all()) if (cursor.crossedForward(cue.tick(), from, currentTick)) cues.add(cue);
            cues.sort(Comparator.comparingLong(EffectCue::tick));
        }
        for (EffectCue cue : cues) EffectCuePlayer.play(viewer, cue);
        cursor.commit(currentTick);
        return cues.size();
    }

    /** Handles a loop seam by traversing the two visible segments without duplicating the seam. */
    public int advanceLoop(Player viewer, Scene scene, EffectPlaybackCursor cursor, double currentTick, double boundary, boolean reverse) {
        Objects.requireNonNull(viewer, "viewer"); Objects.requireNonNull(scene, "scene"); Objects.requireNonNull(cursor, "cursor");
        double from = cursor.previousTick();
        if (!Double.isFinite(currentTick) || !Double.isFinite(boundary)) throw new IllegalArgumentException("ticks must be finite");
        int fired = 0;
        if (reverse) {
            for (EffectCue cue : scene.effects().all()) if (cursor.crossedReverse(cue.tick(), from, boundary)) { EffectCuePlayer.play(viewer, cue); fired++; }
            cursor.nextLoop(); cursor.commit(currentTick);
            for (EffectCue cue : scene.effects().all()) if (cue.tick() >= currentTick && cue.tick() < boundary) { EffectCuePlayer.play(viewer, cue); fired++; }
        } else {
            for (EffectCue cue : scene.effects().all()) if (cursor.crossedForward(cue.tick(), from, boundary)) { EffectCuePlayer.play(viewer, cue); fired++; }
            cursor.nextLoop(); cursor.commit(currentTick);
            for (EffectCue cue : scene.effects().all()) if (cue.tick() > boundary && cue.tick() <= currentTick) { EffectCuePlayer.play(viewer, cue); fired++; }
        }
        return fired;
    }
}
