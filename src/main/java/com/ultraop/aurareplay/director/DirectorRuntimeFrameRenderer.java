package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorManager;
import com.ultraop.aurareplay.actor.ActorSample;
import com.ultraop.aurareplay.actor.VirtualActorBackend;
import com.ultraop.aurareplay.camera.CameraController;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Applies complete deterministic Director frames to viewer-local Minecraft state. */
public final class DirectorRuntimeFrameRenderer {
    private final CameraController camera;
    private final ActorManager actorManager;
    private final VirtualActorBackend actorBackend;
    private final Map<UUID, Set<ActorId>> renderedActors = new HashMap<>();

    public DirectorRuntimeFrameRenderer(CameraController camera, ActorManager actorManager, VirtualActorBackend actorBackend) {
        this.camera = Objects.requireNonNull(camera, "camera");
        this.actorManager = Objects.requireNonNull(actorManager, "actorManager");
        this.actorBackend = Objects.requireNonNull(actorBackend, "actorBackend");
    }

    public boolean render(Player viewer, DirectorRenderFrame frame) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(frame, "frame");
        if (!camera.overrideTransform(viewer, frame.camera())) return false;
        Set<ActorId> current = new HashSet<>();
        for (Map.Entry<ActorId, ActorSample> entry : frame.actors().entrySet()) {
            ActorId id = entry.getKey();
            ActorDefinition actor = actorManager.get(id).orElse(null);
            ActorSample sample = entry.getValue();
            if (actor == null || sample == null) continue;
            if (!actor.visible() || !sample.exists()) {
                actorBackend.destroy(actor, viewer);
                continue;
            }
            actorBackend.spawn(actor, viewer);
            actorBackend.update(actor, viewer, sample.transform());
            actorBackend.updateEquipment(actor, viewer, sample.source());
            current.add(id);
        }
        Set<ActorId> previous = renderedActors.get(viewer.getUniqueId());
        if (previous != null) {
            for (ActorId id : previous) {
                if (current.contains(id)) continue;
                actorManager.get(id).ifPresent(actor -> actorBackend.destroy(actor, viewer));
            }
        }
        if (current.isEmpty()) renderedActors.remove(viewer.getUniqueId());
        else renderedActors.put(viewer.getUniqueId(), current);
        return true;
    }

    public void stop(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        Set<ActorId> previous = renderedActors.remove(viewer.getUniqueId());
        if (previous == null) return;
        for (ActorId id : previous) actorManager.get(id).ifPresent(actor -> actorBackend.destroy(actor, viewer));
    }

    public void clear() { renderedActors.clear(); }
    public int renderedActorCount(Player viewer) { return renderedActors.getOrDefault(viewer.getUniqueId(), Set.of()).size(); }
    public CameraController camera() { return camera; }
    public ActorManager actorManager() { return actorManager; }
    public VirtualActorBackend actorBackend() { return actorBackend; }
}
