package com.ultraop.aurareplay.nms;

import com.google.common.collect.ImmutableListMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Pair;
import com.ultraop.aurareplay.actor.ActorAppearance;
import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorTransform;
import com.ultraop.aurareplay.actor.VirtualActorBackend;
import com.ultraop.aurareplay.recording.EntityFlags;
import com.ultraop.aurareplay.recording.snapshot.EntityIdentitySnapshot;
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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Paper 1.21.11 renderer for viewer-local virtual player actors. */
public final class NmsVirtualActorBackend implements VirtualActorBackend {
    private static final double DEFAULT_NAMETAG_HEIGHT = 2.15d;
    private final Map<UUID, Map<UUID, RenderedActor>> rendered = new HashMap<>();

    @Override public void spawn(ActorDefinition actor, Player viewer) {
        RenderedActor existing = rendered.computeIfAbsent(viewer.getUniqueId(), ignored -> new HashMap<>()).get(actor.id().value());
        if (existing != null) { update(actor, viewer, actor.transform()); return; }
        ServerPlayer viewerHandle = ((CraftPlayer) viewer).getHandle();
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) viewer.getWorld()).getHandle();
        EntityIdentitySnapshot identity = firstIdentity(actor);
        UUID fakeUuid = UUID.nameUUIDFromBytes(("AuraReplay:" + viewer.getUniqueId() + ":" + actor.id()).getBytes(StandardCharsets.UTF_8));
        GameProfile profile = createProfile(fakeUuid, profileName(identity == null ? actor.name() : identity.profileName()), identity);
        ServerPlayer npc = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        ActorTransform transform = actor.transform();
        npc.setPos(transform.x(), transform.y(), transform.z()); npc.setYRot(transform.yaw()); npc.setXRot(transform.pitch()); npc.setYHeadRot(transform.yaw()); npc.setCustomNameVisible(false);
        var scale = npc.getAttribute(Attributes.SCALE); if (scale != null) scale.setBaseValue(transform.scale());
        ServerEntity tracker = new ServerEntity(level, npc, 1, true, new ViewerSynchronizer(viewerHandle), new HashSet<>());
        RenderedActor state = new RenderedActor(npc, tracker, fakeUuid);
        rendered.get(viewer.getUniqueId()).put(actor.id().value(), state);
        viewerHandle.connection.send(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, npc));
        viewerHandle.connection.send(npc.getAddEntityPacket(tracker));
        viewerHandle.connection.send(new ClientboundSetEntityDataPacket(npc.getId(), npc.getEntityData().getNonDefaultValues()));
        if (actor.nameVisible()) spawnNametag(actor, viewer, state, level);
        updateEquipment(actor, viewer, new EntitySnapshot(0, null, org.bukkit.entity.EntityType.PLAYER, transform.x(), transform.y(), transform.z(), transform.yaw(), transform.pitch(), 0, 0, 0, 0, null, true));
    }

    @Override public void update(ActorDefinition actor, Player viewer, ActorTransform transform) {
        RenderedActor state = find(actor, viewer); if (state == null) { spawn(actor, viewer); return; }
        ServerPlayer npc = state.player(); npc.setPos(transform.x(), transform.y(), transform.z()); npc.setYRot(transform.yaw()); npc.setXRot(transform.pitch()); npc.setYHeadRot(transform.yaw());
        var scale = npc.getAttribute(Attributes.SCALE); if (scale != null && scale.getBaseValue() != transform.scale()) scale.setBaseValue(transform.scale());
        state.tracker().sendChanges(); sendEntityDataIfDirty(viewer, npc); updateNametag(actor, viewer, state, transform);
    }

    @Override public void destroy(ActorDefinition actor, Player viewer) {
        Map<UUID, RenderedActor> viewerActors = rendered.get(viewer.getUniqueId()); if (viewerActors == null) return;
        RenderedActor state = viewerActors.remove(actor.id().value()); if (state == null) return;
        var connection = ((CraftPlayer) viewer).getHandle().connection;
        if (state.nametag() != null) connection.send(new ClientboundRemoveEntitiesPacket(state.nametag().getId()));
        connection.send(new ClientboundRemoveEntitiesPacket(state.player().getId())); connection.send(new ClientboundPlayerInfoRemovePacket(List.of(state.profileUuid())));
        if (viewerActors.isEmpty()) rendered.remove(viewer.getUniqueId());
    }

    @Override public void updateIdentity(ActorDefinition actor, Player viewer) {
        RenderedActor state = find(actor, viewer); if (state == null) return; updateNametag(actor, viewer, state, actor.transform());
    }

    @Override public void updateEquipment(ActorDefinition actor, Player viewer, EntitySnapshot snapshot) {
        RenderedActor state = find(actor, viewer); if (state == null) return;
        ActorAppearance appearance = actor.appearance();
        int flags = snapshot == null ? 0 : snapshot.flags();
        if (appearance.invisible() != null) flags = overrideFlag(flags, EntityFlags.INVISIBLE, appearance.invisible());
        if (appearance.glowing() != null) flags = overrideFlag(flags, EntityFlags.GLOWING, appearance.glowing());
        if (appearance.fire() != null) flags = overrideFlag(flags, EntityFlags.ON_FIRE, appearance.fire());
        applyFlags(state.player(), flags);
        if (appearance.pose() != null) state.player().setPose(toNmsPose(appearance.pose()), true);
        EquipmentSnapshot source = snapshot == null ? null : snapshot.equipment();
        EquipmentSnapshot equipment = mergeEquipment(source, appearance);
        if (!sameEquipment(state.equipment(), equipment)) { sendEquipment(viewer, state.player(), equipment); state.setEquipment(equipment); }
        sendEntityDataIfDirty(viewer, state.player());
    }

    private static int overrideFlag(int flags, int flag, boolean value) { return value ? flags | flag : flags & ~flag; }
    private static Pose toNmsPose(org.bukkit.entity.Pose pose) { return Pose.valueOf(pose.name()); }

    private static EquipmentSnapshot mergeEquipment(EquipmentSnapshot source, ActorAppearance a) {
        EquipmentSnapshot s = source == null ? new EquipmentSnapshot(null, null, null, null, null, null) : source;
        return new EquipmentSnapshot(value(a, ActorAppearance.EquipmentSlot.MAIN_HAND, s.mainHand()), value(a, ActorAppearance.EquipmentSlot.OFF_HAND, s.offHand()), value(a, ActorAppearance.EquipmentSlot.HEAD, s.helmet()), value(a, ActorAppearance.EquipmentSlot.CHEST, s.chestplate()), value(a, ActorAppearance.EquipmentSlot.LEGS, s.leggings()), value(a, ActorAppearance.EquipmentSlot.FEET, s.boots()));
    }
    private static org.bukkit.inventory.ItemStack value(ActorAppearance a, ActorAppearance.EquipmentSlot slot, org.bukkit.inventory.ItemStack fallback) { return a.hasEquipmentOverride(slot) ? a.equipment(slot) : fallback; }

    private void spawnNametag(ActorDefinition actor, Player viewer, RenderedActor state, ServerLevel level) {
        if (state.nametag() != null) return; Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level); configureNametag(display, actor); positionNametag(display, actor.transform(), actor.nameHeightOffset());
        ServerPlayer viewerHandle = ((CraftPlayer) viewer).getHandle(); ServerEntity tracker = new ServerEntity(level, display, 1, false, new ViewerSynchronizer(viewerHandle), new HashSet<>()); state.setNametag(display, tracker);
        viewerHandle.connection.send(display.getAddEntityPacket(tracker)); viewerHandle.connection.send(new ClientboundSetEntityDataPacket(display.getId(), display.getEntityData().getNonDefaultValues()));
    }
    private void updateNametag(ActorDefinition actor, Player viewer, RenderedActor state, ActorTransform transform) {
        if (!actor.nameVisible()) { if (state.nametag() != null) { ((CraftPlayer) viewer).getHandle().connection.send(new ClientboundRemoveEntitiesPacket(state.nametag().getId())); state.clearNametag(); } return; }
        if (state.nametag() == null) { spawnNametag(actor, viewer, state, ((CraftWorld) viewer.getWorld()).getHandle()); return; }
        configureNametag(state.nametag(), actor); positionNametag(state.nametag(), transform, actor.nameHeightOffset()); state.nametagTracker().sendChanges(); sendEntityDataIfDirty(viewer, state.nametag());
    }
    private void configureNametag(Display.TextDisplay display, ActorDefinition actor) { TextDisplay bukkitDisplay = (TextDisplay) display.getBukkitEntity(); bukkitDisplay.text(Component.text(actor.namePrefix() + actor.name() + actor.nameSuffix())); bukkitDisplay.setBillboard(org.bukkit.entity.Display.Billboard.CENTER); bukkitDisplay.setShadowed(true); bukkitDisplay.setSeeThrough(true); bukkitDisplay.setDefaultBackground(false); bukkitDisplay.setTextOpacity((byte) -1); bukkitDisplay.setLineWidth(200); }
    private void positionNametag(Display.TextDisplay display, ActorTransform transform, double offset) { display.setPos(transform.x(), transform.y() + DEFAULT_NAMETAG_HEIGHT + transform.scale() * 0.35d + offset, transform.z()); }
    private void applyFlags(ServerPlayer npc, int flags) { Player bukkitPlayer = npc.getBukkitEntity(); bukkitPlayer.setSneaking(hasFlag(flags, EntityFlags.SNEAKING)); bukkitPlayer.setSprinting(hasFlag(flags, EntityFlags.SPRINTING)); bukkitPlayer.setSwimming(hasFlag(flags, EntityFlags.SWIMMING)); bukkitPlayer.setGliding(hasFlag(flags, EntityFlags.GLIDING)); bukkitPlayer.setInvisible(hasFlag(flags, EntityFlags.INVISIBLE)); bukkitPlayer.setGlowing(hasFlag(flags, EntityFlags.GLOWING)); bukkitPlayer.setFireTicks(hasFlag(flags, EntityFlags.ON_FIRE) ? 20 : 0); }
    private void sendEquipment(Player viewer, ServerPlayer npc, EquipmentSnapshot equipment) { EquipmentSnapshot safe = equipment == null ? new EquipmentSnapshot(null, null, null, null, null, null) : equipment; List<Pair<EquipmentSlot, net.minecraft.world.item.ItemStack>> slots = new ArrayList<>(); slots.add(Pair.of(EquipmentSlot.MAINHAND, toNms(safe.mainHand()))); slots.add(Pair.of(EquipmentSlot.OFFHAND, toNms(safe.offHand()))); slots.add(Pair.of(EquipmentSlot.HEAD, toNms(safe.helmet()))); slots.add(Pair.of(EquipmentSlot.CHEST, toNms(safe.chestplate()))); slots.add(Pair.of(EquipmentSlot.LEGS, toNms(safe.leggings()))); slots.add(Pair.of(EquipmentSlot.FEET, toNms(safe.boots()))); ((CraftPlayer) viewer).getHandle().connection.send(new ClientboundSetEquipmentPacket(npc.getId(), slots)); }
    private static net.minecraft.world.item.ItemStack toNms(org.bukkit.inventory.ItemStack item) { return item == null ? net.minecraft.world.item.ItemStack.EMPTY : CraftItemStack.asNMSCopy(item); }
    private static boolean sameEquipment(EquipmentSnapshot a, EquipmentSnapshot b) { if (a == b) return true; if (a == null || b == null) return false; return sameItem(a.mainHand(), b.mainHand()) && sameItem(a.offHand(), b.offHand()) && sameItem(a.helmet(), b.helmet()) && sameItem(a.chestplate(), b.chestplate()) && sameItem(a.leggings(), b.leggings()) && sameItem(a.boots(), b.boots()); }
    private static boolean sameItem(org.bukkit.inventory.ItemStack a, org.bukkit.inventory.ItemStack b) { if (a == b) return true; if (a == null || b == null) return a == null && b == null; return a.isSimilar(b) && a.getAmount() == b.getAmount(); }
    private static boolean hasFlag(int flags, int flag) { return (flags & flag) != 0; }
    private void sendEntityDataIfDirty(Player viewer, ServerPlayer npc) { List<?> values = npc.getEntityData().packDirty(); if (!values.isEmpty()) ((CraftPlayer) viewer).getHandle().connection.send(new ClientboundSetEntityDataPacket(npc.getId(), (List) values)); }
    private void sendEntityDataIfDirty(Player viewer, Display.TextDisplay display) { List<?> values = display.getEntityData().packDirty(); if (!values.isEmpty()) ((CraftPlayer) viewer).getHandle().connection.send(new ClientboundSetEntityDataPacket(display.getId(), (List) values)); }
    private RenderedActor find(ActorDefinition actor, Player viewer) { return rendered.getOrDefault(viewer.getUniqueId(), Map.of()).get(actor.id().value()); }
    private static EntityIdentitySnapshot firstIdentity(ActorDefinition actor) { for (var frame : actor.recording().frames()) for (var entity : frame.entities()) { if (actor.sourceEntityUuid() != null && actor.sourceEntityUuid().equals(entity.uuid())) return entity.identity(); if (actor.sourceEntityId() != null && actor.sourceEntityId().intValue() == entity.entityId()) return entity.identity(); if (actor.sourceEntityUuid() == null && actor.sourceEntityId() == null) return entity.identity(); } return null; }
    private static GameProfile createProfile(UUID uuid, String name, EntityIdentitySnapshot identity) { if (identity == null || identity.skinTextureValue() == null || identity.skinTextureValue().isBlank()) return new GameProfile(uuid, name); Property property = identity.skinTextureSignature() == null || identity.skinTextureSignature().isBlank() ? new Property("textures", identity.skinTextureValue()) : new Property("textures", identity.skinTextureValue(), identity.skinTextureSignature()); PropertyMap properties = new PropertyMap(ImmutableListMultimap.of("textures", property)); return new GameProfile(uuid, name, properties); }
    private static String profileName(String name) { String clean = name == null || name.isBlank() ? "AuraActor" : name; return clean.length() <= 16 ? clean : clean.substring(0, 16); }
    private static final class RenderedActor {
        private final ServerPlayer player; private final ServerEntity tracker; private final UUID profileUuid; private EquipmentSnapshot equipment; private Display.TextDisplay nametag; private ServerEntity nametagTracker;
        private RenderedActor(ServerPlayer player, ServerEntity tracker, UUID profileUuid) { this.player = player; this.tracker = tracker; this.profileUuid = profileUuid; }
        private ServerPlayer player() { return player; } private ServerEntity tracker() { return tracker; } private UUID profileUuid() { return profileUuid; } private EquipmentSnapshot equipment() { return equipment; } private void setEquipment(EquipmentSnapshot e) { equipment = e; } private Display.TextDisplay nametag() { return nametag; } private ServerEntity nametagTracker() { return nametagTracker; } private void setNametag(Display.TextDisplay n, ServerEntity t) { nametag=n; nametagTracker=t; } private void clearNametag() { nametag=null; nametagTracker=null; }
    }
    private static final class ViewerSynchronizer implements ServerEntity.Synchronizer {
        private final ServerPlayer viewer; private ViewerSynchronizer(ServerPlayer viewer) { this.viewer = viewer; }
        @Override public void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet) { viewer.connection.send(packet); }
        @Override public void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet) { viewer.connection.send(packet); }
        @Override public void sendToTrackingPlayersFiltered(Packet<? super ClientGamePacketListener> packet, java.util.function.Predicate<ServerPlayer> predicate) { if (predicate.test(viewer)) viewer.connection.send(packet); }
    }
}
