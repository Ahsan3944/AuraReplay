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
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Inventory event bridge for the contextual Director Studio editor. */
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
        Inventory top = event.getView().getTopInventory();
        if (!studio.isInventory(top)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != top) return;

        DirectorStudioService.Holder holder = (DirectorStudioService.Holder) top.getHolder();
        Scene scene = scenes.get(holder.sceneName()).orElse(null);
        if (scene == null) { player.sendMessage(ChatColor.RED + "Scene is no longer available."); studio.close(player); return; }

        State state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
        state.sceneName = scene.name();

        switch (holder.mode()) {
            case DIRECTOR -> handleDirector(player, scene, state, event.getRawSlot());
            case CONFIG -> handleConfig(player, scene, holder.shotId(), event.getRawSlot());
            case CAMERA -> handleCamera(player, scene, holder.shotId(), event.getRawSlot());
            case TARGET -> handleTarget(player, scene, holder.shotId(), event.getRawSlot());
        }
    }

    private void handleDirector(Player player, Scene scene, State state, int slot) {
        DirectorPlan plan = director.plan(scene);
        DirectorShotEditor editor = new DirectorShotEditor(plan);
        if (slot < 18) {
            if (slot >= plan.shots().size()) return;
            state.selectedShotId = plan.shots().get(slot).id();
            studio.openShotConfig(player, scene.name(), state.selectedShotId);
            return;
        }
        if (state.selectedShotId != null) editor.select(state.selectedShotId);
        switch (slot) {
            case 18 -> state.timelineTick = Math.max(0, state.timelineTick - 20);
            case 19 -> state.timelineTick += 20;
            case 20 -> {
                if (studio.addShot(scene)) {
                    state.selectedShotId = plan.shots().get(plan.shots().size() - 1).id();
                    player.sendMessage(ChatColor.GREEN + "Director shot added.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Cannot add shot: create a camera first.");
                }
            }
            case 21 -> {
                DirectorShot selected = editor.selected();
                if (selected != null && editor.capturePathPoint(localTick(selected, state.timelineTick), CameraTransform.origin(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch()))) {
                    player.sendMessage(ChatColor.GREEN + "Path point captured at tick " + state.timelineTick + ".");
                } else player.sendMessage(ChatColor.YELLOW + "Select a shot first.");
            }
            case 22 -> {
                DirectorShot selected = editor.selected();
                if (selected != null && studio.removePathPoint(scene, selected.id(), localTick(selected, state.timelineTick))) player.sendMessage(ChatColor.YELLOW + "Path point removed.");
                else player.sendMessage(ChatColor.GRAY + "No path point at the current tick.");
            }
            case 23 -> {
                if (director.renderAt(player, scene, state.timelineTick)) player.sendMessage(ChatColor.GREEN + "Director preview active at tick " + state.timelineTick + ".");
                else player.sendMessage(ChatColor.YELLOW + "No active shot at tick " + state.timelineTick + ".");
            }
            case 24 -> { if (editor.cycleType()) player.sendMessage(ChatColor.GREEN + "Shot type: " + editor.selected().type()); }
            case 25 -> { if (editor.cycleTransition()) player.sendMessage(ChatColor.GREEN + "Transition: " + editor.selected().transition()); }
            case 26 -> { director.stop(player); states.remove(player.getUniqueId()); studio.close(player); }
            default -> { }
        }
        if (slot >= 18 && slot <= 25) studio.open(player, scene.name());
    }

    private void handleConfig(Player player, Scene scene, String shotId, int slot) {
        DirectorShot shot = director.plan(scene).get(shotId).orElse(null);
        if (shot == null) { player.sendMessage(ChatColor.RED + "Shot is no longer available."); studio.open(player, scene.name()); return; }
        switch (slot) {
            case 9 -> notifyRange(player, scene, shotId, studio.adjustStart(scene, shotId, -20), "start", -20);
            case 10 -> notifyRange(player, scene, shotId, studio.adjustStart(scene, shotId, 20), "start", 20);
            case 11 -> notifyRange(player, scene, shotId, studio.adjustEnd(scene, shotId, -20), "end", -20);
            case 12 -> notifyRange(player, scene, shotId, studio.adjustEnd(scene, shotId, 20), "end", 20);
            case 13 -> studio.openCameraPicker(player, scene.name(), shotId);
            case 14 -> {
                if (usesTarget(shot.type())) studio.openTargetPicker(player, scene.name(), shotId);
                else player.sendMessage(ChatColor.GRAY + "This shot type does not use a target actor.");
            }
            case 15 -> {
                DirectorShotEditor editor = new DirectorShotEditor(director.plan(scene));
                editor.select(shotId);
                if (editor.cycleType()) {
                    player.sendMessage(ChatColor.GREEN + "Shot type: " + editor.selected().type());
                    studio.openShotConfig(player, scene.name(), shotId);
                }
            }
            case 16 -> {
                DirectorShotEditor editor = new DirectorShotEditor(director.plan(scene));
                editor.select(shotId);
                if (editor.cycleTransition()) {
                    player.sendMessage(ChatColor.GREEN + "Transition: " + editor.selected().transition());
                    studio.openShotConfig(player, scene.name(), shotId);
                }
            }
            case 17 -> {
                if (usesPath(shot.type())) {
                    player.sendMessage(ChatColor.YELLOW + "Path editing remains in the Director timeline: select the shot, move the cursor, then use Add/Remove Path Point.");
                    studio.open(player, scene.name());
                }
            }
            case 22 -> studio.open(player, scene.name());
            case 26 -> { states.remove(player.getUniqueId()); director.stop(player); studio.close(player); }
            default -> { }
        }
    }

    private void handleCamera(Player player, Scene scene, String shotId, int slot) {
        DirectorShot shot = director.plan(scene).get(shotId).orElse(null);
        if (shot == null) { player.sendMessage(ChatColor.RED + "Shot is no longer available."); studio.open(player, scene.name()); return; }
        if (slot < 18) {
            var list = studio.cameras();
            if (slot >= list.size()) return;
            var camera = list.get(slot);
            if (studio.setCamera(scene, shotId, camera.id())) {
                player.sendMessage(ChatColor.GREEN + "Camera assigned: " + camera.id());
                studio.openShotConfig(player, scene.name(), shotId);
            } else player.sendMessage(ChatColor.RED + "Camera assignment failed; the camera reference was rejected.");
            return;
        }
        if (slot == 18) studio.openShotConfig(player, scene.name(), shotId);
        else if (slot == 26) { states.remove(player.getUniqueId()); director.stop(player); studio.close(player); }
    }

    private void handleTarget(Player player, Scene scene, String shotId, int slot) {
        DirectorShot shot = director.plan(scene).get(shotId).orElse(null);
        if (shot == null) { player.sendMessage(ChatColor.RED + "Shot is no longer available."); studio.open(player, scene.name()); return; }
        if (!usesTarget(shot.type())) {
            player.sendMessage(ChatColor.YELLOW + "This shot type does not accept a target actor.");
            studio.openShotConfig(player, scene.name(), shotId);
            return;
        }
        if (slot < 18) {
            var list = studio.actors(scene);
            if (slot >= list.size()) return;
            var actor = list.get(slot);
            String actorId = actor.id().toString();
            if (studio.setTarget(scene, shotId, actorId)) {
                player.sendMessage(ChatColor.GREEN + "Target assigned: " + actor.name());
                studio.openShotConfig(player, scene.name(), shotId);
            } else player.sendMessage(ChatColor.RED + "Target assignment failed; the actor is not part of this scene.");
            return;
        }
        if (slot == 18) {
            if (studio.clearTarget(scene, shotId)) {
                player.sendMessage(ChatColor.YELLOW + "Target cleared.");
                studio.openShotConfig(player, scene.name(), shotId);
            } else player.sendMessage(ChatColor.RED + "Unable to clear target.");
        } else if (slot == 26) studio.openShotConfig(player, scene.name(), shotId);
    }

    private void notifyRange(Player player, Scene scene, String shotId, boolean changed, String edge, long delta) {
        DirectorShot shot = director.plan(scene).get(shotId).orElse(null);
        if (!changed || shot == null) {
            player.sendMessage(ChatColor.YELLOW + "Cannot move " + edge + " by " + delta + " ticks: the resulting shot would overlap another shot or become invalid.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "Shot " + edge + " moved to " + (edge.equals("start") ? shot.startTick() : shot.endTick()) + ".");
        studio.openShotConfig(player, scene.name(), shotId);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!studio.isInventory(event.getInventory())) return;
        // Do not eagerly discard state: opening a contextual child panel fires a close event
        // for the parent inventory. State is explicitly discarded by the Close action.
    }

    private static boolean usesTarget(DirectorShot.Type type) { return type == DirectorShot.Type.FOLLOW || type == DirectorShot.Type.LOOK_AT || type == DirectorShot.Type.ORBIT; }
    private static boolean usesPath(DirectorShot.Type type) { return type == DirectorShot.Type.DOLLY || type == DirectorShot.Type.RAIL || type == DirectorShot.Type.SPLINE; }
    private static long localTick(DirectorShot shot, long sceneTick) { return Math.max(0, sceneTick - shot.startTick()); }
    private static final class State { private String sceneName; private String selectedShotId; private long timelineTick; }
}
