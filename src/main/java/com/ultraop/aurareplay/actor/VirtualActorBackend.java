package com.ultraop.aurareplay.actor;

import com.ultraop.aurareplay.recording.snapshot.EntitySnapshot;
import org.bukkit.entity.Player;

/** Rendering boundary. Implementations may use ProtocolLib or version-specific NMS. */
public interface VirtualActorBackend {
    void spawn(ActorDefinition actor, Player viewer);

    void update(ActorDefinition actor, Player viewer, ActorTransform transform);

    /** Render a complete sampled actor state without advancing playback. */
    default void update(ActorDefinition actor, Player viewer, ActorSample sample) {
        update(actor, viewer, sample.transform());
        updateEquipment(actor, viewer, sample.source());
    }

    void destroy(ActorDefinition actor, Player viewer);

    void updateIdentity(ActorDefinition actor, Player viewer);

    /** Apply the sampled equipment/state snapshot to the rendered actor. */
    void updateEquipment(ActorDefinition actor, Player viewer, EntitySnapshot snapshot);
}
