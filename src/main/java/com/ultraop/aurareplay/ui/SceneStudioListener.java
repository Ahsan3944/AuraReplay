package com.ultraop.aurareplay.ui;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.camera.CameraDefinition;
import com.ultraop.aurareplay.core.AuraEngine;
import com.ultraop.aurareplay.scene.Scene;
import com.ultraop.aurareplay.timeline.TimelineMarker;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Viewer-local Scene Project/Editor surface layered on top of the Studio UI. */
public final class SceneStudioListener implements Listener {
    private static final String PROJECT = ChatColor.DARK_AQUA + "Studio / Project";
    private static final String SCENE_PREFIX = ChatColor.DARK_AQUA + "Studio / Scene / ";
    private final AuraEngine engine;
    private final Map<UUID, String> selectedScenes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> timelineTicks = new ConcurrentHashMap<>();

    public SceneStudioListener(AuraEngine engine) { this.engine = engine; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        String title = event.getView().getTitle();
        if (title.equals(ChatColor.DARK_AQUA + "AuraReplay Studio")) {
            if (event.getRawSlot() == 20) {
                event.setCancelled(true);
                renderProject(player);
            }
            return;
        }
        if (!title.equals(PROJECT) && !title.startsWith(SCENE_PREFIX)) return;
        event.setCancelled(true);
        if (title.equals(PROJECT)) projectClick(player, event.getRawSlot());
        else sceneClick(player, event.getRawSlot());
    }

    private void projectClick(Player player, int slot) {
        var service = engine.sceneStudioService();
        var scenes = service.scenes().stream().toList();
        if (slot >= 0 && slot < Math.min(21, scenes.size())) {
            Scene scene = scenes.get(slot);
            selectedScenes.put(player.getUniqueId(), scene.name());
            timelineTicks.put(player.getUniqueId(), scene.timeline().inPoint());
            renderScene(player, scene);
            return;
        }
        if (slot == 22) {
            String name = uniqueSceneName(service);
            Scene scene = service.create(name);
            selectedScenes.put(player.getUniqueId(), name);
            timelineTicks.put(player.getUniqueId(), 0L);
            player.sendMessage(ChatColor.GREEN + "Created scene: " + name);
            renderScene(player, scene);
            return;
        }
        if (slot == 23) {
            String selected = selectedScenes.get(player.getUniqueId());
            if (selected != null && service.delete(selected)) {
                selectedScenes.remove(player.getUniqueId());
                timelineTicks.remove(player.getUniqueId());
                player.sendMessage(ChatColor.YELLOW + "Deleted scene: " + selected);
            }
            renderProject(player);
            return;
        }
        if (slot == 26) {
            selectedScenes.remove(player.getUniqueId());
            timelineTicks.remove(player.getUniqueId());
            engine.studioController().open(player);
        }
    }

    private void sceneClick(Player player, int slot) {
        Scene scene = selectedScene(player);
        if (scene == null) { renderProject(player); return; }
        var service = engine.sceneStudioService();
        var editor = engine.sceneTimelineService().editor(scene);
        long tick = timelineTicks.getOrDefault(player.getUniqueId(), scene.timeline().inPoint());
        try {
            if (slot >= 0 && slot < 12) {
                var actors = engine.actorManager().all().stream().sorted(Comparator.comparing(a -> a.id().toString())).toList();
                if (slot < actors.size()) {
                    ActorDefinition actor = actors.get(slot);
                    service.toggleActor(scene, actor.id());
                }
            } else if (slot == 12) {
                var cameras = engine.cameraManager().all().stream().sorted(Comparator.comparing(CameraDefinition::id)).toList();
                if (!cameras.isEmpty()) {
                    String current = scene.cameraId();
                    int index = -1;
                    for (int i = 0; i < cameras.size(); i++) if (cameras.get(i).id().equals(current)) index = i;
                    service.assignCamera(scene, cameras.get((index + 1) % cameras.size()).id());
                }
            } else if (slot == 13) {
                service.clearCamera(scene);
            } else if (slot == 14) {
                editor.setSpeed(scene.timeline(), Math.max(0.1d, scene.timeline().playbackSpeed() - 0.1d));
            } else if (slot == 15) {
                editor.setSpeed(scene.timeline(), scene.timeline().playbackSpeed() + 0.1d);
            } else if (slot == 16) {
                editor.setLoop(scene.timeline(), !scene.timeline().loop());
            } else if (slot == 17) {
                editor.setReverse(scene.timeline(), !scene.timeline().reverse());
            } else if (slot == 18) {
                engine.sceneManager().play(player, scene.name());
            } else if (slot == 19) {
                engine.sceneManager().stop(player);
            } else if (slot == 20) {
                tick = Math.min(scene.timeline().durationTicks(), tick + 20);
                timelineTicks.put(player.getUniqueId(), tick);
            } else if (slot == 21) {
                tick = Math.max(scene.timeline().inPoint(), tick - 20);
                timelineTicks.put(player.getUniqueId(), tick);
            } else if (slot == 22) {
                editor.addMarker(scene.timeline(), new TimelineMarker("studio-" + tick, tick, "MARKER @ " + tick));
            } else if (slot == 23) {
                var markers = scene.timeline().markers();
                if (!markers.isEmpty()) editor.removeMarker(scene.timeline(), markers.get(markers.size() - 1).id());
            } else if (slot == 24) {
                editor.undo(scene.timeline());
            } else if (slot == 25) {
                editor.redo(scene.timeline());
            } else if (slot == 26) {
                renderProject(player);
                return;
            }
        } catch (IllegalArgumentException ex) {
            player.sendMessage(ChatColor.RED + ex.getMessage());
        }
        renderScene(player, scene);
    }

    private Scene selectedScene(Player player) {
        String name = selectedScenes.get(player.getUniqueId());
        return name == null ? null : engine.sceneStudioService().get(name).orElse(null);
    }

    private void renderProject(Player player) {
        Inventory inventory = inventory(PROJECT);
        var scenes = engine.sceneStudioService().scenes().stream().toList();
        for (int i = 0; i < Math.min(21, scenes.size()); i++) {
            Scene scene = scenes.get(i);
            item(inventory, i, Material.BOOK, scene.name(),
                    "Actors: " + scene.actorIds().size(),
                    "Camera: " + (scene.cameraId() == null ? "none" : scene.cameraId()),
                    "Duration: " + scene.timeline().durationTicks() + " ticks");
        }
        item(inventory, 22, Material.WRITABLE_BOOK, "Create Scene", "Creates a unique scene name");
        item(inventory, 23, Material.BARRIER, "Delete Selected", "Selected: " + selectedScenes.getOrDefault(player.getUniqueId(), "none"));
        item(inventory, 26, Material.ARROW, "Back", "Return to Studio");
        player.openInventory(inventory);
    }

    private void renderScene(Player player, Scene scene) {
        Inventory inventory = inventory(SCENE_PREFIX + scene.name());
        var actors = engine.actorManager().all().stream().sorted(Comparator.comparing(a -> a.id().toString())).toList();
        for (int i = 0; i < Math.min(12, actors.size()); i++) {
            ActorDefinition actor = actors.get(i);
            boolean included = scene.actorIds().contains(actor.id());
            item(inventory, i, included ? Material.LIME_DYE : Material.GRAY_DYE,
                    (included ? "Included: " : "Available: ") + actor.name(), "Toggle scene membership", "Actor: " + actor.id());
        }
        item(inventory, 12, Material.SPYGLASS, "Assign Camera", "Current: " + (scene.cameraId() == null ? "none" : scene.cameraId()));
        item(inventory, 13, Material.BARRIER, "Unassign Camera");
        item(inventory, 14, Material.SPECTRAL_ARROW, "Speed -", "Current: " + fmt(scene.timeline().playbackSpeed()));
        item(inventory, 15, Material.ARROW, "Speed +", "Current: " + fmt(scene.timeline().playbackSpeed()));
        item(inventory, 16, Material.REPEATER, "Loop", "Current: " + scene.timeline().loop());
        item(inventory, 17, Material.CLOCK, "Reverse", "Current: " + scene.timeline().reverse());
        item(inventory, 18, Material.LIME_DYE, "Play Scene", "Viewer-local scene preview");
        item(inventory, 19, Material.RED_DYE, "Stop Scene");
        long tick = timelineTicks.getOrDefault(player.getUniqueId(), scene.timeline().inPoint());
        item(inventory, 20, Material.CLOCK, "Timeline +20", "Current tick: " + tick);
        item(inventory, 21, Material.SPECTRAL_ARROW, "Timeline -20", "Current tick: " + tick);
        item(inventory, 22, Material.NAME_TAG, "Add Marker", "Tick: " + tick, "Markers: " + scene.timeline().markers().size());
        item(inventory, 23, Material.REDSTONE_TORCH, "Remove Last Marker", "Markers: " + scene.timeline().markers().size());
        item(inventory, 24, Material.PAPER, "Undo", "Non-destructive timeline edit");
        item(inventory, 25, Material.ENDER_CHEST, "Redo", "Non-destructive timeline edit");
        item(inventory, 26, Material.ARROW, "Back", "Return to Project");
        player.openInventory(inventory);
    }

    private String uniqueSceneName(SceneStudioService service) {
        String base = "scene";
        int n = 1;
        while (service.get(base + "-" + n).isPresent()) n++;
        return base + "-" + n;
    }

    private static Inventory inventory(String title) { return Bukkit.createInventory(null, 27, title); }
    private static void item(Inventory inventory, int slot, Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + name);
        meta.setLore(Arrays.stream(lore).map(line -> ChatColor.GRAY + line).toList());
        stack.setItemMeta(meta);
        inventory.setItem(slot, stack);
    }
    private static String fmt(double value) { return String.format(java.util.Locale.ROOT, "%.1f", value); }
}
