package com.ultraop.aurareplay.ui;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.motion.MotionLayer;
import com.ultraop.aurareplay.motion.MotionStack;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-game non-destructive motion-layer editor for the selected actor. */
public final class MotionStudioService {
    private final StudioController controller;
    private final Map<UUID, String> selectedLayers = new ConcurrentHashMap<>();

    public MotionStudioService(StudioController controller) { this.controller = controller; }

    public void open(Player player) {
        ActorDefinition actor = actor(player);
        if (actor == null) { controller.click(player, 26); return; }
        selectedLayers.remove(player.getUniqueId());
        renderLayers(player, actor);
    }

    public void click(Player player, int slot) {
        ActorDefinition actor = actor(player);
        if (actor == null) { controller.click(player, 26); return; }
        String selected = selectedLayers.get(player.getUniqueId());
        if (selected == null) {
            if (slot == 26) { controller.click(player, 26); return; }
            if (slot >= 0 && slot < actor.motionStack().size()) {
                MotionLayer layer = actor.motionStack().layers().get(slot);
                selectedLayers.put(player.getUniqueId(), layer.id());
                renderEditor(player, actor, layer);
                return;
            }
            if (slot >= 18 && slot <= 25) { addLayer(player, actor, typeForSlot(slot)); }
            return;
        }

        MotionStack stack = actor.motionStack();
        MotionLayer layer = find(stack, selected);
        if (layer == null) { selectedLayers.remove(player.getUniqueId()); renderLayers(player, actor); return; }
        if (slot == 26) { selectedLayers.remove(player.getUniqueId()); renderLayers(player, actor); return; }
        if (slot == 20) stack.setEnabled(layer.id(), !layer.enabled());
        else if (slot == 21) {
            stack.remove(layer.id());
            selectedLayers.remove(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Removed motion layer: " + layer.id());
            renderLayers(player, actor);
            return;
        } else if (slot == 22) move(stack, layer.id(), -1);
        else if (slot == 23) move(stack, layer.id(), 1);
        else if (slot == 24) stack.replace(adjust(layer, false));
        else if (slot == 25) stack.replace(adjust(layer, true));
        else if (slot == 10) stack.replace(adjustRange(layer, -5, 0));
        else if (slot == 11) stack.replace(adjustRange(layer, 5, 0));
        else if (slot == 12) stack.replace(adjustRange(layer, 0, -5));
        else if (slot == 13) stack.replace(adjustRange(layer, 0, 5));
        else if (slot == 14) stack.replace(adjustComponent(layer, -1));
        else if (slot == 15) stack.replace(adjustComponent(layer, 1));
        else if (slot == 16) stack.replace(adjustValue(layer, -1));
        else if (slot == 17) stack.replace(adjustValue(layer, 1));
        MotionLayer updated = find(stack, selected);
        if (updated == null) { selectedLayers.remove(player.getUniqueId()); renderLayers(player, actor); }
        else renderEditor(player, actor, updated);
    }

    public void close(Player player) { selectedLayers.remove(player.getUniqueId()); }

    private void addLayer(Player player, ActorDefinition actor, MotionLayer.Type type) {
        String id = uniqueId(actor.motionStack(), type.name().toLowerCase());
        long end = Math.max(20L, actor.recording().frameCount() - 1L);
        MotionLayer layer = switch (type) {
            case POSITION_OFFSET -> MotionLayer.positionOffset(id, 0, 0, 0, 0, end);
            case ROTATION_OFFSET -> MotionLayer.rotationOffset(id, 0, 0, 0, 0, end);
            case SCALE -> MotionLayer.scale(id, 1.0, 0, end);
            case TIME_OFFSET -> MotionLayer.timeOffset(id, 0, 0, end);
            case SPEED_MULTIPLIER -> MotionLayer.speed(id, 1.0, 0, end);
            case REVERSE -> MotionLayer.reverse(id, 0, end);
            case MIRROR_X -> MotionLayer.mirrorX(id, 0, end);
            case MIRROR_Z -> MotionLayer.mirrorZ(id, 0, end);
            case PATH_SHIFT -> MotionLayer.pathShift(id, 0, 0, 0, 0, end);
            case LOOP -> MotionLayer.loop(id, 1, 0, end);
            case BLEND -> MotionLayer.blend(id, 1.0, 0, end);
            case TRIM -> MotionLayer.trim(id, 0, end);
            case RETIME -> MotionLayer.retime(id, 1.0, 0, end);
            case FREEZE_POSE -> MotionLayer.freezePose(id, 0, 0, end);
            case POSE_SNAPSHOT -> MotionLayer.poseSnapshot(id, 0, 0, end);
        };
        actor.motionStack().add(layer);
        selectedLayers.put(player.getUniqueId(), id);
        player.sendMessage(ChatColor.GREEN + "Added motion layer: " + type.name());
        renderEditor(player, actor, layer);
    }

    private static MotionLayer.Type typeForSlot(int slot) {
        return switch (slot) {
            case 18 -> MotionLayer.Type.POSITION_OFFSET;
            case 19 -> MotionLayer.Type.ROTATION_OFFSET;
            case 20 -> MotionLayer.Type.SCALE;
            case 21 -> MotionLayer.Type.TIME_OFFSET;
            case 22 -> MotionLayer.Type.SPEED_MULTIPLIER;
            case 23 -> MotionLayer.Type.REVERSE;
            case 24 -> MotionLayer.Type.MIRROR_X;
            default -> MotionLayer.Type.MIRROR_Z;
        };
    }

    private static String uniqueId(MotionStack stack, String base) {
        String id = base; int n = 2;
        while (find(stack, id) != null) id = base + "-" + n++;
        return id;
    }

    private static MotionLayer find(MotionStack stack, String id) {
        return stack.layers().stream().filter(layer -> layer.id().equals(id)).findFirst().orElse(null);
    }

    private static void move(MotionStack stack, String id, int delta) {
        int index = -1;
        for (int i = 0; i < stack.layers().size(); i++) if (stack.layers().get(i).id().equals(id)) index = i;
        if (index >= 0) stack.move(id, Math.max(0, Math.min(stack.size() - 1, index + delta)));
    }

    private static MotionLayer adjustRange(MotionLayer l, long startDelta, long endDelta) {
        long start = Math.max(0, l.startTick() + startDelta);
        long end = Math.max(start, l.endTick() + endDelta);
        return copy(l, start, end, l.x(), l.y(), l.z(), l.yaw(), l.pitch(), l.roll(), l.value());
    }

    private static MotionLayer adjustComponent(MotionLayer l, double delta) {
        double x = l.x(), y = l.y(), z = l.z();
        float yaw = l.yaw(), pitch = l.pitch(), roll = l.roll();
        if (l.type() == MotionLayer.Type.POSITION_OFFSET || l.type() == MotionLayer.Type.PATH_SHIFT) x += delta;
        else if (l.type() == MotionLayer.Type.ROTATION_OFFSET) yaw += (float) (delta * 5);
        else return adjustValue(l, delta * .1);
        return copy(l, l.startTick(), l.endTick(), x, y, z, yaw, pitch, roll, l.value());
    }

    private static MotionLayer adjustValue(MotionLayer l, double delta) {
        boolean scaled = l.type() == MotionLayer.Type.SPEED_MULTIPLIER || l.type() == MotionLayer.Type.SCALE || l.type() == MotionLayer.Type.RETIME;
        double value = l.value() + delta * (scaled ? .1 : 1);
        if (scaled) value = Math.max(.1, value);
        if (l.type() == MotionLayer.Type.BLEND) value = Math.max(.01, Math.min(1, value));
        return copy(l, l.startTick(), l.endTick(), l.x(), l.y(), l.z(), l.yaw(), l.pitch(), l.roll(), value);
    }

    private static MotionLayer adjust(MotionLayer l, boolean positive) { return adjustValue(l, positive ? 1 : -1); }

    private static MotionLayer copy(MotionLayer l, long start, long end, double x, double y, double z, float yaw, float pitch, float roll, double value) {
        return new MotionLayer(l.id(), l.type(), x, y, z, yaw, pitch, roll, value, start, end, l.enabled());
    }

    private void renderLayers(Player player, ActorDefinition actor) {
        Inventory inventory = inventory(ChatColor.DARK_AQUA + "Motion Layers / " + actor.name());
        var layers = actor.motionStack().layers();
        for (int i = 0; i < Math.min(18, layers.size()); i++) {
            MotionLayer l = layers.get(i);
            item(inventory, i, l.enabled() ? Material.LIME_DYE : Material.GRAY_DYE, (i + 1) + ". " + l.type(), "ID: " + l.id(), "Range: " + l.startTick() + " → " + l.endTick(), "Click to edit");
        }
        item(inventory, 18, Material.COMPASS, "+ Position Offset", "Add editable position layer");
        item(inventory, 19, Material.CLOCK, "+ Rotation Offset", "Add editable rotation layer");
        item(inventory, 20, Material.SLIME_BALL, "+ Scale", "Add scale layer");
        item(inventory, 21, Material.SPECTRAL_ARROW, "+ Time Offset", "Add timeline offset layer");
        item(inventory, 22, Material.REPEATER, "+ Speed", "Add speed multiplier layer");
        item(inventory, 23, Material.CLOCK, "+ Reverse", "Add reverse layer");
        item(inventory, 24, Material.OBSERVER, "+ Mirror X", "Add X mirror layer");
        item(inventory, 25, Material.OBSERVER, "+ Mirror Z", "Add Z mirror layer");
        item(inventory, 26, Material.ARROW, "Back", "Return to actor motion");
        player.openInventory(inventory);
    }

    private void renderEditor(Player player, ActorDefinition actor, MotionLayer l) {
        Inventory inventory = inventory(ChatColor.DARK_AQUA + "Motion Layer / " + l.type());
        item(inventory, 10, Material.REDSTONE, "Start -5", "Start: " + l.startTick());
        item(inventory, 11, Material.EMERALD, "Start +5", "Start: " + l.startTick());
        item(inventory, 12, Material.REDSTONE, "End -5", "End: " + l.endTick());
        item(inventory, 13, Material.EMERALD, "End +5", "End: " + l.endTick());
        item(inventory, 14, Material.REDSTONE_TORCH, "Component -", component(l));
        item(inventory, 15, Material.TORCH, "Component +", component(l));
        item(inventory, 16, Material.REDSTONE, "Value -", "Value: " + fmt(l.value()));
        item(inventory, 17, Material.EMERALD, "Value +", "Value: " + fmt(l.value()));
        item(inventory, 20, l.enabled() ? Material.LIME_DYE : Material.GRAY_DYE, l.enabled() ? "Disable Layer" : "Enable Layer", "Layer: " + l.id());
        item(inventory, 21, Material.BARRIER, "Delete Layer", "Remove this non-destructive operation");
        item(inventory, 22, Material.ARROW, "Move Up", "Stack order: " + index(actor.motionStack(), l.id()));
        item(inventory, 23, Material.SPECTRAL_ARROW, "Move Down", "Stack order: " + index(actor.motionStack(), l.id()));
        item(inventory, 24, Material.BOOK, "Decrease Value", "Generic fine adjustment");
        item(inventory, 25, Material.WRITABLE_BOOK, "Increase Value", "Generic fine adjustment");
        item(inventory, 26, Material.ARROW, "Back", "Return to motion layers");
        player.openInventory(inventory);
    }

    private static int index(MotionStack stack, String id) {
        for (int i = 0; i < stack.layers().size(); i++) if (stack.layers().get(i).id().equals(id)) return i + 1;
        return -1;
    }

    private static String component(MotionLayer l) {
        return switch (l.type()) {
            case POSITION_OFFSET, PATH_SHIFT -> "X: " + fmt(l.x()) + " Y: " + fmt(l.y()) + " Z: " + fmt(l.z());
            case ROTATION_OFFSET -> "Yaw: " + fmt(l.yaw()) + " Pitch: " + fmt(l.pitch()) + " Roll: " + fmt(l.roll());
            default -> "Value: " + fmt(l.value());
        };
    }

    private ActorDefinition actor(Player player) {
        StudioSession session = controller.session(player);
        if (session == null) return null;
        ActorId id = session.selectedActor();
        return id == null ? null : controller.engine().actorManager().get(id).orElse(null);
    }

    private static Inventory inventory(String title) { return Bukkit.createInventory(new Holder(), 27, title); }
    private static void item(Inventory inventory, int slot, Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + name);
        meta.setLore(Arrays.stream(lore).map(v -> ChatColor.GRAY + v).toList());
        stack.setItemMeta(meta);
        inventory.setItem(slot, stack);
    }
    private static String fmt(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }
    private static String fmt(float value) { return String.format(java.util.Locale.ROOT, "%.1f", value); }
    private static final class Holder implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
}
