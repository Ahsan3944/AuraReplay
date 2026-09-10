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
        List<EffectCue> cues = crossed(scene, cursor, from, currentTick, reverse);
        for (EffectCue cue : cues) EffectCuePlayer.play(viewer, cue);
        cursor.commit(currentTick);
        return cues.size();
    }

    /**
     * Handles a loop seam using two traversal segments. Forward playback traverses
     * (from,end] then (start,current]; reverse traverses [end,from) then [current,end).
     * The loop endpoint is therefore never duplicated by the wrapped segment.
     */
    public int advanceLoop(Player viewer, Scene scene, EffectPlaybackCursor cursor,
                           double currentTick, double loopStart, double loopEnd, boolean reverse) {
        Objects.requireNonNull(viewer, "viewer"); Objects.requireNonNull(scene, "scene"); Objects.requireNonNull(cursor, "cursor");
        if (!Double.isFinite(currentTick) || !Double.isFinite(loopStart) || !Double.isFinite(loopEnd))
            throw new IllegalArgumentException("ticks must be finite");
        if (loopEnd <= loopStart) throw new IllegalArgumentException("loopEnd must be > loopStart");
        if (!cursor.initialized()) { cursor.reset(currentTick); return 0; }
        double from = cursor.previousTick();
        int fired = 0;
        if (reverse) {
            List<EffectCue> first = crossed(scene, cursor, from, loopStart, true);
            for (EffectCue cue : first) { EffectCuePlayer.play(viewer, cue); fired++; }
            cursor.nextLoop();
            List<EffectCue> second = crossed(scene, cursor, loopEnd, currentTick, true);
            for (EffectCue cue : second) { EffectCuePlayer.play(viewer, cue); fired++; }
        } else {
            List<EffectCue> first = crossed(scene, cursor, from, loopEnd, false);
            for (EffectCue cue : first) { EffectCuePlayer.play(viewer, cue); fired++; }
            cursor.nextLoop();
            List<EffectCue> second = crossed(scene, cursor, loopStart, currentTick, false);
            for (EffectCue cue : second) { EffectCuePlayer.play(viewer, cue); fired++; }
        }
        cursor.commit(currentTick);
        return fired;
    }

    /** Backward-compatible overload for callers that supply only the active boundary. */
    public int advanceLoop(Player viewer, Scene scene, EffectPlaybackCursor cursor,
                           double currentTick, double boundary, boolean reverse) {
        if (reverse) return advanceLoop(viewer, scene, cursor, currentTick, boundary, Double.POSITIVE_INFINITY, true);
        return advanceLoop(viewer, scene, cursor, currentTick, Double.NEGATIVE_INFINITY, boundary, false);
    }

    private List<EffectCue> crossed(Scene scene, EffectPlaybackCursor cursor, double from, double to, boolean reverse) {
        List<EffectCue> cues = new ArrayList<>();
        if (reverse) {
            for (EffectCue cue : scene.effects().all()) if (cursor.crossedReverse(cue.tick(), from, to)) cues.add(cue);
            cues.sort(Comparator.comparingLong(EffectCue::tick).reversed());
        } else {
            for (EffectCue cue : scene.effects().all()) if (cursor.crossedForward(cue.tick(), from, to)) cues.add(cue);
            cues.sort(Comparator.comparingLong(EffectCue::tick));
        }
        return cues;
    }
}
