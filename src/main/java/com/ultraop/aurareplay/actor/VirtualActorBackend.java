package com.ultraop.aurareplay.actor;

import org.bukkit.entity.Player;

/** Rendering boundary. Implementations may use ProtocolLib or version-specific NMS. */
public interface VirtualActorBackend {
    void spawn(ActorDefinition actor, Player viewer);
    void update(ActorDefinition actor, Player viewer, ActorTransform transform);
    void destroy(ActorDefinition actor, Player viewer);
    void updateIdentity(ActorDefinition actor, Player viewer);
    void updateEquipment(ActorDefinition actor, Player viewer);
}
