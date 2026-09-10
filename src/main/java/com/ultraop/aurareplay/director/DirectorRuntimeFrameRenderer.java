package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorManager;
import com.ultraop.aurareplay.actor.ActorSample;
import com.ultraop.aurareplay.actor.VirtualActorBackend;
import com.ultraop.aurareplay.camera.CameraController;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;

/** Applies a complete deterministic Director frame to the viewer-local Minecraft runtime. */
public final class DirectorRuntimeFrameRenderer {
    private final CameraController camera;
    private final ActorManager actorManager;
    private final VirtualActorBackend actorBackend;

    public DirectorRuntimeFrameRenderer(CameraController camera, ActorManager actorManager, VirtualActorBackend actorBackend) {
        this.camera = Objects.requireNonNull(camera, "camera");
        this.actorManager = Objects.requireNonNull(actorManager, "actorManager");
        this.actorBackend = Objects.requireNonNull(actorBackend, "actorBackend");
    }

    public boolean render(Player viewer, DirectorRenderFrame frame) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(frame, "frame");
        if (!camera.overrideTransform(viewer, frame.camera())) return false;
        for (Map.Entry<com.ultraop.aurareplay.actor.ActorId, ActorSample> entry : frame.actors().entrySet()) {
            ActorDefinition actor = actorManager.get(entry.getKey()).orElse(null);
            ActorSample sample = entry.getValue();
            if (actor == null || sample == null) continue;
            if (!actor.visible() || !sample.exists()) {
                actorBackend.destroy(actor, viewer);
                continue;
            }
            actorBackend.spawn(actor, viewer);
            actorBackend.update(actor, viewer, sample.transform());
            actorBackend.updateEquipment(actor, viewer, sample.source());
        }
        return true;
    }

    public CameraController camera() { return camera; }
    public ActorManager actorManager() { return actorManager; }
    public VirtualActorBackend actorBackend() { return actorBackend; }
}
