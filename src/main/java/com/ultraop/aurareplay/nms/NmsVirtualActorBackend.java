package com.ultraop.aurareplay.nms;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorTransform;
import com.ultraop.aurareplay.actor.VirtualActorBackend;
import com.ultraop.aurareplay.recording.EntityFlags;
import com.ultraop.aurareplay.recording.snapshot.EntitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.EquipmentSnapshot;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Paper 1.21.11 renderer for virtual player actors.
 *
 * The fake ServerPlayer is never inserted into the world. Private ServerEntity
 * trackers emit only to the selected viewer, keeping actor visibility viewer-local.
 */
public final class NmsVirtualActorBackend implements VirtualActorBackend {
    private static final double DEFAULT_NAMETAG_HEIGHT = 2.15d;
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

        ServerPlayer viewerHandle = ((CraftPlayer) viewer).getHandle();
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) viewer.getWorld()).getHandle();

        UUID fakeUuid = UUID.nameUUIDFromBytes(
                ("AuraReplay:" + viewer.getUniqueId() + ":" + actor.id())
                        .getBytes(StandardCharsets.UTF_8)
        );
        GameProfile profile = new GameProfile(fakeUuid, profileName(actor.name()));
        ServerPlayer npc = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        ActorTransform transform = actor.transform();
        npc.setPos(transform.x(), transform.y(), transform.z());
        npc.setYRot(transform.yaw());
        npc.setXRot(transform.pitch());
        npc.setYHeadRot(transform.yaw());
        // The dedicated TextDisplay below is the actor nametag. Keeping the
        // ServerPlayer custom name hidden gives us a real height/offset control.
        npc.setCustomNameVisible(false);

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

        viewerHandle.connection.send(new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, npc));
        viewerHandle.connection.send(npc.getAddEntityPacket(tracker));
        viewerHandle.connection.send(new ClientboundSetEntityDataPacket(
                npc.getId(), npc.getEntityData().getNonDefaultValues()));

        if (actor.nameVisible()) spawnNametag(actor, viewer, state, level);
    }

    @Override
    public void update(ActorDefinition actor, Player viewer, ActorTransform transform) {
        RenderedActor state = find(actor, viewer);
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
        updateNametag(actor, viewer, state, transform);
    }

    @Override
    public void destroy(ActorDefinition actor, Player viewer) {
        Map<UUID, RenderedActor> viewerActors = rendered.get(viewer.getUniqueId());
        if (viewerActors == null) return;
        RenderedActor state = viewerActors.remove(actor.id().value());
        if (state == null) return;

        var connection = ((CraftPlayer) viewer).getHandle().connection;
        if (state.nametag() != null) {
            connection.send(new ClientboundRemoveEntitiesPacket(state.nametag().getId()));
        }
        connection.send(new ClientboundRemoveEntitiesPacket(state.player().getId()));
        connection.send(new ClientboundPlayerInfoRemovePacket(List.of(state.profileUuid())));

        if (viewerActors.isEmpty()) rendered.remove(viewer.getUniqueId());
    }

    @Override
    public void updateIdentity(ActorDefinition actor, Player viewer) {
        RenderedActor state = find(actor, viewer);
        if (state == null) return;

        updateNametag(actor, viewer, state, actor.transform());
    }

    @Override
    public void updateEquipment(ActorDefinition actor, Player viewer, EntitySnapshot snapshot) {
        RenderedActor state = find(actor, viewer);
        if (state == null || snapshot == null) return;

        applyFlags(state.player(), snapshot.flags());

        EquipmentSnapshot equipment = snapshot.equipment();
        if (!sameEquipment(state.equipment(), equipment)) {
            sendEquipment(viewer, state.player(), equipment);
            state.setEquipment(equipment);
        }

        sendEntityDataIfDirty(viewer, state.player());
    }

    private void spawnNametag(ActorDefinition actor, Player viewer, RenderedActor state, ServerLevel level) {
        if (state.nametag() != null) return;

        Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        configureNametag(display, actor);
        positionNametag(display, actor.transform());

        ServerPlayer viewerHandle = ((CraftPlayer) viewer).getHandle();
        ServerEntity tracker = new ServerEntity(
                level,
                display,
                1,
                false,
                new ViewerSynchronizer(viewerHandle),
                new HashSet<>()
        );
        state.setNametag(display, tracker);

        viewerHandle.connection.send(display.getAddEntityPacket(tracker));
        viewerHandle.connection.send(new ClientboundSetEntityDataPacket(
                display.getId(), display.getEntityData().getNonDefaultValues()));
    }

    private void updateNametag(ActorDefinition actor, Player viewer, RenderedActor state, ActorTransform transform) {
        if (!actor.nameVisible()) {
            if (state.nametag() != null) {
                ((CraftPlayer) viewer).getHandle().connection.send(
                        new ClientboundRemoveEntitiesPacket(state.nametag().getId()));
                state.clearNametag();
            }
            return;
        }

        if (state.nametag() == null) {
            spawnNametag(actor, viewer, state, ((CraftWorld) viewer.getWorld()).getHandle());
            return;
        }

        TextDisplay display = (TextDisplay) state.nametag().getBukkitEntity();
        configureNametag(display, actor);
        positionNametag(state.nametag(), transform);
        state.nametagTracker().sendChanges();
        sendEntityDataIfDirty(viewer, state.nametag());
    }

    private void configureNametag(Display.TextDisplay display, ActorDefinition actor) {
        display.setText(Component.text(actor.namePrefix() + actor.name() + actor.nameSuffix()));
        display.setBillboard(Display.Billboard.CENTER);
        display.setShadowed(true);
        display.setSeeThrough(true);
        display.setDefaultBackground(false);
        display.setTextOpacity((byte) -1);
        display.setLineWidth(200);
        display.setTransformation(new Transformation(
                new Vector3f(0.0f, 0.0f, 0.0f),
                new AxisAngle4f(),
                new Vector3f(1.0f, 1.0f, 1.0f),
                new AxisAngle4f()
        ));
    }

    private void configureNametag(TextDisplay display, ActorDefinition actor) {
        display.text(Component.text(actor.namePrefix() + actor.name() + actor.nameSuffix()));
        display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        display.setShadowed(true);
        display.setSeeThrough(true);
        display.setDefaultBackground(false);
        display.setTextOpacity((byte) -1);
        display.setLineWidth(200);
    }

    private void positionNametag(Display.TextDisplay display, ActorTransform transform) {
        display.setPos(transform.x(), transform.y() + DEFAULT_NAMETAG_HEIGHT + transform.scale() * 0.35d, transform.z());
    }

    private void positionNametag(TextDisplay display, ActorTransform transform) {
        display.teleport(display.getLocation().clone().add(
                transform.x() - display.getLocation().getX(),
                transform.y() + DEFAULT_NAMETAG_HEIGHT + transform.scale() * 0.35d - display.getLocation().getY(),
                transform.z() - display.getLocation().getZ()
        ));
    }

    private void positionNametag(ServerEntity tracker, ActorTransform transform) {
        Display.TextDisplay display = (Display.TextDisplay) tracker.getEntity();
        positionNametag(display, transform);
    }

    private void applyFlags(ServerPlayer npc, int flags) {
        Player bukkitPlayer = npc.getBukkitEntity();
        bukkitPlayer.setSneaking(hasFlag(flags, EntityFlags.SNEAKING));
        bukkitPlayer.setSprinting(hasFlag(flags, EntityFlags.SPRINTING));
        bukkitPlayer.setSwimming(hasFlag(flags, EntityFlags.SWIMMING));
        bukkitPlayer.setGliding(hasFlag(flags, EntityFlags.GLIDING));
        bukkitPlayer.setInvisible(hasFlag(flags, EntityFlags.INVISIBLE));
        bukkitPlayer.setGlowing(hasFlag(flags, EntityFlags.GLOWING));
        bukkitPlayer.setFireTicks(hasFlag(flags, EntityFlags.ON_FIRE) ? 20 : 0);
    }

    private void sendEquipment(Player viewer, ServerPlayer npc, EquipmentSnapshot equipment) {
        EquipmentSnapshot safe = equipment == null
                ? new EquipmentSnapshot(null, null, null, null, null, null)
                : equipment;

        List<Pair<EquipmentSlot, net.minecraft.world.item.ItemStack>> slots = new ArrayList<>();
        slots.add(Pair.of(EquipmentSlot.MAINHAND, toNms(safe.mainHand())));
        slots.add(Pair.of(EquipmentSlot.OFFHAND, toNms(safe.offHand())));
        slots.add(Pair.of(EquipmentSlot.HEAD, toNms(safe.helmet())));
        slots.add(Pair.of(EquipmentSlot.CHEST, toNms(safe.chestplate())));
        slots.add(Pair.of(EquipmentSlot.LEGS, toNms(safe.leggings())));
        slots.add(Pair.of(EquipmentSlot.FEET, toNms(safe.boots())));

        ((CraftPlayer) viewer).getHandle().connection.send(
                new ClientboundSetEquipmentPacket(npc.getId(), slots));
    }

    private static net.minecraft.world.item.ItemStack toNms(org.bukkit.inventory.ItemStack item) {
        return item == null ? net.minecraft.world.item.ItemStack.EMPTY : CraftItemStack.asNMSCopy(item);
    }

    private static boolean sameEquipment(EquipmentSnapshot a, EquipmentSnapshot b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return sameItem(a.mainHand(), b.mainHand()) && sameItem(a.offHand(), b.offHand())
                && sameItem(a.helmet(), b.helmet()) && sameItem(a.chestplate(), b.chestplate())
                && sameItem(a.leggings(), b.leggings()) && sameItem(a.boots(), b.boots());
    }

    private static boolean sameItem(org.bukkit.inventory.ItemStack a, org.bukkit.inventory.ItemStack b) {
        if (a == b) return true;
        if (a == null || b == null) return a == null && b == null;
        return a.isSimilar(b) && a.getAmount() == b.getAmount();
    }

    private static boolean hasFlag(int flags, int flag) { return (flags & flag) != 0; }

    private void sendEntityDataIfDirty(Player viewer, ServerPlayer npc) {
        List<?> values = npc.getEntityData().packDirty();
        if (!values.isEmpty()) {
            ((CraftPlayer) viewer).getHandle().connection.send(
                    new ClientboundSetEntityDataPacket(npc.getId(), (List) values));
        }
    }

    private void sendEntityDataIfDirty(Player viewer, Display.TextDisplay display) {
        List<?> values = display.getEntityData().packDirty();
        if (!values.isEmpty()) {
            ((CraftPlayer) viewer).getHandle().connection.send(
                    new ClientboundSetEntityDataPacket(display.getId(), (List) values));
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

    private static final class RenderedActor {
        private final ServerPlayer player;
        private final ServerEntity tracker;
        private final UUID profileUuid;
        private EquipmentSnapshot equipment;
        private Display.TextDisplay nametag;
        private ServerEntity nametagTracker;

        private RenderedActor(ServerPlayer player, ServerEntity tracker, UUID profileUuid) {
            this.player = player;
            this.tracker = tracker;
            this.profileUuid = profileUuid;
        }

        private ServerPlayer player() { return player; }
        private ServerEntity tracker() { return tracker; }
        private UUID profileUuid() { return profileUuid; }
        private EquipmentSnapshot equipment() { return equipment; }
        private void setEquipment(EquipmentSnapshot equipment) { this.equipment = equipment; }
        private Display.TextDisplay nametag() { return nametag; }
        private ServerEntity nametagTracker() { return nametagTracker; }
        private void setNametag(Display.TextDisplay nametag, ServerEntity tracker) {
            this.nametag = nametag;
            this.nametagTracker = tracker;
        }
        private void clearNametag() {
            this.nametag = null;
            this.nametagTracker = null;
        }
    }

    private static final class ViewerSynchronizer implements ServerEntity.Synchronizer {
        private final ServerPlayer viewer;

        private ViewerSynchronizer(ServerPlayer viewer) { this.viewer = viewer; }

        @Override
        public void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet) { viewer.connection.send(packet); }

        @Override
        public void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet) { viewer.connection.send(packet); }

        @Override
        public void sendToTrackingPlayersFiltered(
                Packet<? super ClientGamePacketListener> packet,
                java.util.function.Predicate<ServerPlayer> predicate
        ) {
            if (predicate.test(viewer)) viewer.connection.send(packet);
        }
    }
}
