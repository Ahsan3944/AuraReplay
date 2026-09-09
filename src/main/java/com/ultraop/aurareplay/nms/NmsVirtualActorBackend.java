package com.ultraop.aurareplay.nms;

import com.mojang.authlib.GameProfile;
import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorTransform;
import com.ultraop.aurareplay.actor.VirtualActorBackend;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Paper 1.21.11 renderer for virtual player actors.
 *
 * The fake ServerPlayer is never inserted into the world. A private ServerEntity
 * tracker emits only to the selected viewer, keeping actor visibility viewer-local.
 */
public final class NmsVirtualActorBackend implements VirtualActorBackend {
    private final Map<UUID, Map<UUID, RenderedActor>> rendered = new HashMap<>();

    @Override
    public void spawn(ActorDefinition actor, Player viewer) {
        RenderedActor existing = rendered
                .computeIfAbsent(viewer.getUniqueId(), ignored -> new HashMap<>())
                .get(actor.id().value());
        if (existing != null) {
            update(actor, viewer, actor.transform());
            return;
        }

        if (sourceType(actor) != EntityType.PLAYER) {
            throw new UnsupportedOperationException(
                    "NmsVirtualActorBackend currently renders PLAYER actors; " +
                    "entity morph adapters will extend this backend."
            );
        }

        ServerPlayer viewerHandle = ((CraftPlayer) viewer).getHandle();
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) viewer.getWorld()).getHandle();

        UUID fakeUuid = UUID.nameUUIDFromBytes(
                ("AuraReplay:" + viewer.getUniqueId() + ":" + actor.id())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        GameProfile profile = new GameProfile(fakeUuid, profileName(actor.name()));
        ServerPlayer npc = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        ActorTransform transform = actor.transform();
        npc.setPos(transform.x(), transform.y(), transform.z());
        npc.setYRot(transform.yaw());
        npc.setXRot(transform.pitch());
        npc.setYHeadRot(transform.yaw());
        npc.setCustomName(net.minecraft.network.chat.Component.literal(actor.name()));
        npc.setCustomNameVisible(actor.nameVisible());

        var scale = npc.getAttribute(Attributes.SCALE);
        if (scale != null) scale.setBaseValue(transform.scale());

        Set<ServerPlayerConnection> trackedConnections = new HashSet<>();
        ServerEntity tracker = new ServerEntity(
                level,
                npc,
                1,
                true,
                new ViewerSynchronizer(viewerHandle),
                trackedConnections
        );

        RenderedActor state = new RenderedActor(npc, tracker, fakeUuid);
        rendered.get(viewer.getUniqueId()).put(actor.id().value(), state);

        viewerHandle.connection.send(
                new ClientboundPlayerInfoUpdatePacket(
                        ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                        npc
                )
        );
        viewerHandle.connection.send(npc.getAddEntityPacket(tracker));
        viewerHandle.connection.send(
                new ClientboundSetEntityDataPacket(
                        npc.getId(),
                        npc.getEntityData().getNonDefaultValues()
                )
        );
    }

    @Override
    public void update(ActorDefinition actor, Player viewer, ActorTransform transform) {
        RenderedActor state = rendered
                .getOrDefault(viewer.getUniqueId(), Map.of())
                .get(actor.id().value());
        if (state == null) {
            spawn(actor, viewer);
            return;
        }

        ServerPlayer npc = state.player();
        npc.setPos(transform.x(), transform.y(), transform.z());
        npc.setYRot(transform.yaw());
        npc.setXRot(transform.pitch());
        npc.setYHeadRot(transform.yaw());

        var scale = npc.getAttribute(Attributes.SCALE);
        if (scale != null && scale.getBaseValue() != transform.scale()) {
            scale.setBaseValue(transform.scale());
        }

        state.tracker().sendChanges();
        sendEntityDataIfDirty(viewer, npc);
    }

    @Override
    public void destroy(ActorDefinition actor, Player viewer) {
        Map<UUID, RenderedActor> viewerActors = rendered.get(viewer.getUniqueId());
        if (viewerActors == null) return;
        RenderedActor state = viewerActors.remove(actor.id().value());
        if (state == null) return;

        var connection = ((CraftPlayer) viewer).getHandle().connection;
        connection.send(new ClientboundRemoveEntitiesPacket(state.player().getId()));
        connection.send(new ClientboundPlayerInfoRemovePacket(List.of(state.profileUuid())));

        if (viewerActors.isEmpty()) rendered.remove(viewer.getUniqueId());
    }

    @Override
    public void updateIdentity(ActorDefinition actor, Player viewer) {
        RenderedActor state = find(actor, viewer);
        if (state == null) return;
        ServerPlayer npc = state.player();
        npc.setCustomName(net.minecraft.network.chat.Component.literal(actor.name()));
        npc.setCustomNameVisible(actor.nameVisible());
        sendEntityDataIfDirty(viewer, npc);
    }

    @Override
    public void updateEquipment(ActorDefinition actor, Player viewer) {
        // Equipment synchronization is the next renderer layer; this method remains
        // separate so the actor domain never depends on NMS equipment types.
    }

    private void sendEntityDataIfDirty(Player viewer, ServerPlayer npc) {
        List<?> values = npc.getEntityData().packDirty();
        if (!values.isEmpty()) {
            ((CraftPlayer) viewer).getHandle().connection.send(
                    new ClientboundSetEntityDataPacket(npc.getId(), (List) values)
            );
        }
    }

    private RenderedActor find(ActorDefinition actor, Player viewer) {
        return rendered.getOrDefault(viewer.getUniqueId(), Map.of()).get(actor.id().value());
    }

    private EntityType sourceType(ActorDefinition actor) {
        for (var frame : actor.recording().frames()) {
            for (var entity : frame.entities()) {
                if (actor.sourceEntityUuid() != null && actor.sourceEntityUuid().equals(entity.uuid())) return entity.type();
                if (actor.sourceEntityId() != null && actor.sourceEntityId().intValue() == entity.entityId()) return entity.type();
                if (actor.sourceEntityUuid() == null && actor.sourceEntityId() == null) return entity.type();
            }
        }
        return EntityType.PLAYER;
    }

    private static String profileName(String name) {
        String clean = name == null || name.isBlank() ? "AuraActor" : name;
        return clean.length() <= 16 ? clean : clean.substring(0, 16);
    }

    private record RenderedActor(ServerPlayer player, ServerEntity tracker, UUID profileUuid) {}

    private static final class ViewerSynchronizer implements ServerEntity.Synchronizer {
        private final ServerPlayer viewer;

        private ViewerSynchronizer(ServerPlayer viewer) {
            this.viewer = viewer;
        }

        @Override
        public void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet) {
            viewer.connection.send(packet);
        }

        @Override
        public void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet) {
            viewer.connection.send(packet);
        }

        @Override
        public void sendToTrackingPlayersFiltered(
                Packet<? super ClientGamePacketListener> packet,
                java.util.function.Predicate<ServerPlayer> predicate
        ) {
            if (predicate.test(viewer)) viewer.connection.send(packet);
        }
    }
}
