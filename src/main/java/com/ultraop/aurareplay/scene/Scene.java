package com.ultraop.aurareplay.scene;

import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.effects.EffectCue;
import com.ultraop.aurareplay.effects.EffectCueTrack;
import com.ultraop.aurareplay.timeline.Timeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Scene composition containing ordered actor references, a camera reference, effects and a timeline. */
public final class Scene {
    private final String name;
    private final List<ActorId> actorIds = new ArrayList<>();
    private final Timeline timeline = new Timeline();
    private final EffectCueTrack effects = new EffectCueTrack();
    private String cameraId;

    public Scene(String name) { this.name = normalizeName(name); }
    public String name() { return name; }
    public List<ActorId> actorIds() { return List.copyOf(actorIds); }
    public Timeline timeline() { return timeline; }
    public EffectCueTrack effects() { return effects; }
    public String cameraId() { return cameraId; }

    public boolean addActor(ActorId actorId) {
        Objects.requireNonNull(actorId, "actorId");
        if (actorIds.contains(actorId)) return false;
        actorIds.add(actorId);
        return true;
    }
    public boolean removeActor(ActorId actorId) { return actorIds.remove(actorId); }
    public void clearActors() { actorIds.clear(); }
    public void setCameraId(String cameraId) { this.cameraId = cameraId == null || cameraId.isBlank() ? null : cameraId.trim(); }

    public boolean addEffect(EffectCue cue) { return effects.add(cue); }
    public boolean removeEffect(String id) { return effects.remove(id); }

    private static String normalizeName(String name) {
        Objects.requireNonNull(name, "name");
        String value = name.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("scene name cannot be empty");
        return value;
    }
}
