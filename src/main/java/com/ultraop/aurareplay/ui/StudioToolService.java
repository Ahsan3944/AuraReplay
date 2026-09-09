package com.ultraop.aurareplay.ui;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.core.AuraEngine;
import com.ultraop.aurareplay.camera.CameraDefinition;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Executes concrete Studio operations against the same engine services used by commands. */
public final class StudioToolService {
    private final AuraEngine engine;

    public StudioToolService(AuraEngine engine) { this.engine = engine; }

    public boolean toggleActorVisibility(ActorId id) {
        ActorDefinition actor = engine.actorManager().get(id).orElse(null);
        if (actor == null) return false;
        actor.setVisible(!actor.visible());
        return true;
    }

    public boolean setActorScale(ActorId id, double scale) {
        ActorDefinition actor = engine.actorManager().get(id).orElse(null);
        if (actor == null) return false;
        actor.setTransform(actor.transform().withScale(scale));
        return true;
    }

    public boolean setActorPlaybackSpeed(ActorId id, double speed) {
        ActorDefinition actor = engine.actorManager().get(id).orElse(null);
        if (actor == null) return false;
        actor.setPlaybackSpeed(speed);
        return true;
    }

    public boolean startRecording(Player player) {
        if (engine.tickRecorder().isRecording()) return false;
        engine.tickRecorder().track(player);
        engine.tickRecorder().start();
        return true;
    }

    public boolean stopRecording(String name) {
        if (!engine.tickRecorder().isRecording()) return false;
        var recording = engine.tickRecorder().stop(name);
        if (recording == null) return false;
        engine.recordingManager().register(recording);
        return true;
    }

    public int cameraCount() { return engine.cameraManager().all().size(); }
}
