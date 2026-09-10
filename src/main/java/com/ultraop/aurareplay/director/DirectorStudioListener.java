package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import com.ultraop.aurareplay.scene.Scene;
import com.ultraop.aurareplay.scene.SceneManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Inventory event bridge for the Director Studio editor. */
public final class DirectorStudioListener implements Listener {
    private final DirectorStudioService studio;
    private final SceneManager scenes;
    private final DirectorController director;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public DirectorStudioListener(DirectorStudioService studio, SceneManager scenes, DirectorController director) {
        this.studio = studio; this.scenes = scenes; this.director = director;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!studio.isInventory(event.getView().getTopInventory())) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        String sceneName = sceneName(event.getView().getTitle());
        if (sceneName == null) return;
        Scene scene = scenes.get(sceneName).orElse(null);
        if (scene == null) return;
        State state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
        state.sceneName = scene.name();
        DirectorPlan plan = director.plan(scene);
        if (event.getRawSlot() < 18) {
            if (event.getRawSlot() < plan.shots().size()) state.selectedShotId = plan.shots().get(event.getRawSlot()).id();
            return;
        }
        DirectorShotEditor editor = new DirectorShotEditor(plan);
        if (state.selectedShotId != null) editor.select(state.selectedShotId);
        switch (event.getRawSlot()) {
            case 18 -> state.timelineTick = Math.max(0, state.timelineTick - 20);
            case 19 -> state.timelineTick += 20;
            case 20 -> { if (studio.addShot(scene)) { state.selectedShotId = plan.shots().get(plan.shots().size() - 1).id(); player.sendMessage(ChatColor.GREEN + "Director shot added."); } else player.sendMessage(ChatColor.YELLOW + "Cannot add shot: create a camera first."); }
            case 21 -> { DirectorShot selected = editor.selected(); if (selected != null && editor.capturePathPoint(localTick(selected, state.timelineTick), CameraTransform.origin(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch()))) player.sendMessage(ChatColor.GREEN + "Path point captured at tick " + state.timelineTick + "."); else player.sendMessage(ChatColor.YELLOW + "Select a shot first."); }
            case 22 -> { DirectorShot selected = editor.selected(); if (selected != null && studio.removePathPoint(scene, selected.id(), localTick(selected, state.timelineTick))) player.sendMessage(ChatColor.YELLOW + "Path point removed."); else player.sendMessage(ChatColor.GRAY + "No path point at the current tick."); }
            case 23 -> { if (director.renderAt(player, scene, state.timelineTick)) player.sendMessage(ChatColor.GREEN + "Director preview active at tick " + state.timelineTick + "."); else player.sendMessage(ChatColor.YELLOW + "No active shot at tick " + state.timelineTick + "."); }
            case 24 -> { if (editor.cycleType()) player.sendMessage(ChatColor.GREEN + "Shot type: " + editor.selected().type()); }
            case 25 -> { if (editor.cycleTransition()) player.sendMessage(ChatColor.GREEN + "Transition: " + editor.selected().transition()); }
            case 26 -> { director.stop(player); states.remove(player.getUniqueId()); player.closeInventory(); return; }
            default -> { return; }
        }
        studio.open(player, scene.name());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (studio.isInventory(event.getInventory())) states.remove(player.getUniqueId());
    }

    private static String sceneName(String title) {
        String prefix = ChatColor.DARK_AQUA + "Director / ";
        return title != null && title.startsWith(prefix) ? title.substring(prefix.length()) : null;
    }

    private static long localTick(DirectorShot shot, long sceneTick) { return Math.max(0, sceneTick - shot.startTick()); }
    private static final class State { private String sceneName; private String selectedShotId; private long timelineTick; }
}
