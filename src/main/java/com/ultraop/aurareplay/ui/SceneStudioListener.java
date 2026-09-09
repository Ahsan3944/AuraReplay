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
import org.bukkit.event.inventory.ClickType;
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
        else sceneClick(player, event.getRawSlot(), event.getClick());
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

    private void sceneClick(Player player, int slot, ClickType click) {
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
                editor.setRange(scene.timeline(), scene.timeline().inPoint(), scene.timeline().outPoint());
                engine.sceneManager().play(player, scene.name());
            } else if (slot == 19) {
                engine.sceneManager().stop(player);
            } else if (slot == 20) {
                if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                    editor.setDuration(scene.timeline(), scene.timeline().durationTicks() + 20);
                } else {
                    editor.setDuration(scene.timeline(), Math.max(0, scene.timeline().durationTicks() - 20));
                }
                tick = Math.min(tick, scene.timeline().durationTicks());
                timelineTicks.put(player.getUniqueId(), tick);
            } else if (slot == 21) {
                if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                    long out = Math.max(scene.timeline().inPoint(), Math.min(tick, scene.timeline().durationTicks()));
                    editor.setRange(scene.timeline(), scene.timeline().inPoint(), out);
                } else {
                    long in = Math.max(0, Math.min(tick, scene.timeline().outPoint()));
                    editor.setRange(scene.timeline(), in, scene.timeline().outPoint());
                }
            } else if (slot == 22) {
                if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                    var markers = scene.timeline().markers();
                    if (!markers.isEmpty()) editor.removeMarker(scene.timeline(), markers.get(markers.size() - 1).id());
                } else {
                    editor.addMarker(scene.timeline(), new TimelineMarker("studio-" + tick, tick, "MARKER @ " + tick));
                }
            } else if (slot == 23) {
                if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) editor.redo(scene.timeline());
                else editor.undo(scene.timeline());
            } else if (slot == 24) {
                engine.sceneManager().play(player, scene.name());
            } else if (slot == 25) {
                engine.sceneManager().stop(player);
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
        item(inventory, 20, Material.CLOCK, "Duration ±20", "Left: -20 ticks", "Right: +20 ticks", "Current: " + scene.timeline().durationTicks());
        item(inventory, 21, Material.COMPARATOR, "In / Out", "Left: set In at current tick", "Right: set Out at current tick", "Current: " + scene.timeline().inPoint() + " → " + scene.timeline().outPoint(), "Timeline cursor: " + tick);
        item(inventory, 22, Material.NAME_TAG, "Marker", "Left: add marker", "Right: remove last marker", "Markers: " + scene.timeline().markers().size(), "Tick: " + tick);
        item(inventory, 23, Material.PAPER, "History", "Left: Undo", "Right: Redo");
        item(inventory, 24, Material.LIME_DYE, "Play Preview", "Starts at scene In point");
        item(inventory, 25, Material.RED_DYE, "Stop Preview");
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
