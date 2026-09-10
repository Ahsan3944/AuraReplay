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
        if (director.active(player) && director.currentScene(player).equals(scene.name())) state.timelineTick = Math.round(director.currentTick(player));

        switch (holder.mode()) {
            case DIRECTOR -> handleDirector(player, scene, state, event.getRawSlot());
            case CONFIG -> handleConfig(player, scene, holder.shotId(), state, event.getRawSlot());
            case CAMERA -> handleCamera(player, scene, holder.shotId(), event.getRawSlot());
            case TARGET -> handleTarget(player, scene, holder.shotId(), event.getRawSlot());
            case PATH -> handlePath(player, scene, holder.shotId(), state, event.getRawSlot());
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
            case 19 -> state.timelineTick = Math.min(Math.max(0, plan.durationTicks() - 1), state.timelineTick + 20);
            case 20 -> {
                if (studio.addShot(scene)) {
                    state.selectedShotId = plan.shots().get(plan.shots().size() - 1).id();
                    player.sendMessage(ChatColor.GREEN + "Director shot added.");
                } else player.sendMessage(ChatColor.YELLOW + "Cannot add shot: create a camera first.");
            }
            case 21 -> {
                DirectorShot selected = editor.selected();
                if (selected != null && studio.addPathPoint(scene, selected.id(), localTick(selected, state.timelineTick), CameraTransform.origin(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch()))) player.sendMessage(ChatColor.GREEN + "Path point captured at tick " + state.timelineTick + ".");
                else player.sendMessage(ChatColor.YELLOW + "Select a path-enabled shot first.");
            }
            case 22 -> {
                DirectorShot selected = editor.selected();
                if (selected != null && studio.removePathPoint(scene, selected.id(), localTick(selected, state.timelineTick))) player.sendMessage(ChatColor.YELLOW + "Path point removed.");
                else player.sendMessage(ChatColor.GRAY + "No path point at the current local tick.");
            }
            case 23 -> {
                if (director.active(player)) {
                    if (director.playing(player)) { director.pause(player); player.sendMessage(ChatColor.YELLOW + "Director paused at tick " + Math.round(director.currentTick(player)) + "."); }
                    else if (director.play(player)) player.sendMessage(ChatColor.GREEN + "Director playback resumed.");
                    else player.sendMessage(ChatColor.YELLOW + "No director playback session. Move the cursor onto a shot and press Preview first.");
                } else {
                    if (director.start(player, scene, state.timelineTick)) player.sendMessage(ChatColor.GREEN + "Director playback started at tick " + state.timelineTick + ".");
                    else player.sendMessage(ChatColor.YELLOW + "No active shot at tick " + state.timelineTick + ".");
                }
            }
            case 24 -> {
                if (director.renderAt(player, scene, state.timelineTick)) player.sendMessage(ChatColor.GREEN + "Director preview active at tick " + state.timelineTick + ".");
                else player.sendMessage(ChatColor.YELLOW + "No active shot at tick " + state.timelineTick + ".");
            }
            case 25 -> { director.stop(player); player.sendMessage(ChatColor.YELLOW + "Director stopped."); }
            case 26 -> { director.stop(player); states.remove(player.getUniqueId()); studio.close(player); }
            default -> { }
        }
        if (slot >= 18 && slot <= 25) studio.open(player, scene.name());
    }

    private void handleConfig(Player player, Scene scene, String shotId, State state, int slot) {
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
                DirectorShotEditor editor = new DirectorShotEditor(director.plan(scene)); editor.select(shotId);
                if (editor.cycleType()) { player.sendMessage(ChatColor.GREEN + "Shot type: " + editor.selected().type()); studio.openShotConfig(player, scene.name(), shotId); }
            }
            case 16 -> {
                DirectorShotEditor editor = new DirectorShotEditor(director.plan(scene)); editor.select(shotId);
                if (editor.cycleTransition()) { player.sendMessage(ChatColor.GREEN + "Transition: " + editor.selected().transition()); studio.openShotConfig(player, scene.name(), shotId); }
            }
            case 17 -> {
                if (usesPath(shot.type())) studio.openPathEditor(player, scene.name(), shotId);
                else player.sendMessage(ChatColor.GRAY + "This shot type does not use a path.");
            }
            case 22 -> studio.open(player, scene.name());
            case 26 -> { states.remove(player.getUniqueId()); director.stop(player); studio.close(player); }
            default -> { }
        }
    }

    private void handlePath(Player player, Scene scene, String shotId, State state, int slot) {
        DirectorShot shot = director.plan(scene).get(shotId).orElse(null);
        if (shot == null) { player.sendMessage(ChatColor.RED + "Shot is no longer available."); studio.open(player, scene.name()); return; }
        if (!usesPath(shot.type())) { player.sendMessage(ChatColor.YELLOW + "This shot type does not use a path."); studio.openShotConfig(player, scene.name(), shotId); return; }
        var points = shot.path().points();
        if (slot < 18) {
            if (slot >= points.size()) return;
            state.selectedPathTick = (long) points.get(slot).tick();
            state.timelineTick = shot.startTick() + state.selectedPathTick;
            player.sendMessage(ChatColor.GREEN + "Selected path point at local tick " + state.selectedPathTick + ".");
            studio.openPathEditor(player, scene.name(), shotId);
            return;
        }
        switch (slot) {
            case 18 -> state.timelineTick = Math.max(shot.startTick(), state.timelineTick - 20);
            case 19 -> state.timelineTick = Math.min(shot.endTick() - 1, state.timelineTick + 20);
            case 20 -> {
                long tick = localTick(shot, state.timelineTick);
                if (studio.addPathPoint(scene, shotId, tick, currentTransform(player))) { state.selectedPathTick = tick; player.sendMessage(ChatColor.GREEN + "Path point captured at local tick " + tick + "."); }
                else player.sendMessage(ChatColor.RED + "Unable to capture path point.");
            }
            case 21 -> {
                if (state.selectedPathTick < 0) player.sendMessage(ChatColor.YELLOW + "Select a path point first.");
                else if (studio.addPathPoint(scene, shotId, state.selectedPathTick, currentTransform(player))) player.sendMessage(ChatColor.GREEN + "Selected point recaptured at local tick " + state.selectedPathTick + ".");
                else player.sendMessage(ChatColor.RED + "Unable to recapture selected point.");
            }
            case 22 -> moveSelected(player, scene, shot, state, -20);
            case 23 -> moveSelected(player, scene, shot, state, 20);
            case 24 -> {
                if (state.selectedPathTick < 0) player.sendMessage(ChatColor.YELLOW + "Select a path point first.");
                else if (studio.removePathPoint(scene, shotId, state.selectedPathTick)) { player.sendMessage(ChatColor.YELLOW + "Removed path point at local tick " + state.selectedPathTick + "."); state.selectedPathTick = -1; }
                else player.sendMessage(ChatColor.GRAY + "Selected path point no longer exists.");
            }
            case 25 -> {
                if (studio.clearPath(scene, shotId)) { state.selectedPathTick = -1; player.sendMessage(ChatColor.YELLOW + "Cleared all path points."); }
                else player.sendMessage(ChatColor.RED + "Unable to clear path.");
            }
            case 26 -> studio.openShotConfig(player, scene.name(), shotId);
            default -> { }
        }
        if (slot >= 18 && slot <= 25) studio.openPathEditor(player, scene.name(), shotId);
    }

    private void moveSelected(Player player, Scene scene, DirectorShot shot, State state, long delta) {
        if (state.selectedPathTick < 0) { player.sendMessage(ChatColor.YELLOW + "Select a path point first."); return; }
        long target = state.selectedPathTick + delta;
        if (target < 0 || target >= shot.durationTicks()) { player.sendMessage(ChatColor.YELLOW + "The selected point cannot move outside the shot range."); return; }
        if (studio.movePathPoint(scene, shot.id(), state.selectedPathTick, target)) { state.selectedPathTick = target; state.timelineTick = shot.startTick() + target; player.sendMessage(ChatColor.GREEN + "Selected point moved to local tick " + target + "."); }
        else player.sendMessage(ChatColor.YELLOW + "Cannot move the selected point there: another point already exists at that tick.");
    }

    private static CameraTransform currentTransform(Player player) { return CameraTransform.origin(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch()); }

    private void handleCamera(Player player, Scene scene, String shotId, int slot) {
        DirectorShot shot = director.plan(scene).get(shotId).orElse(null);
        if (shot == null) { player.sendMessage(ChatColor.RED + "Shot is no longer available."); studio.open(player, scene.name()); return; }
        if (slot < 18) {
            var list = studio.cameras(); if (slot >= list.size()) return;
            var camera = list.get(slot);
            if (studio.setCamera(scene, shotId, camera.id())) { player.sendMessage(ChatColor.GREEN + "Camera assigned: " + camera.id()); studio.openShotConfig(player, scene.name(), shotId); }
            else player.sendMessage(ChatColor.RED + "Camera assignment failed; the camera reference was rejected.");
            return;
        }
        if (slot == 18) studio.openShotConfig(player, scene.name(), shotId);
        else if (slot == 26) { states.remove(player.getUniqueId()); director.stop(player); studio.close(player); }
    }

    private void handleTarget(Player player, Scene scene, String shotId, int slot) {
        DirectorShot shot = director.plan(scene).get(shotId).orElse(null);
        if (shot == null) { player.sendMessage(ChatColor.RED + "Shot is no longer available."); studio.open(player, scene.name()); return; }
        if (!usesTarget(shot.type())) { player.sendMessage(ChatColor.YELLOW + "This shot type does not accept a target actor."); studio.openShotConfig(player, scene.name(), shotId); return; }
        if (slot < 18) {
            var list = studio.actors(scene); if (slot >= list.size()) return;
            var actor = list.get(slot); String actorId = actor.id().toString();
            if (studio.setTarget(scene, shotId, actorId)) { player.sendMessage(ChatColor.GREEN + "Target assigned: " + actor.name()); studio.openShotConfig(player, scene.name(), shotId); }
            else player.sendMessage(ChatColor.RED + "Target assignment failed; the actor is not part of this scene.");
            return;
        }
        if (slot == 18) {
            if (studio.clearTarget(scene, shotId)) { player.sendMessage(ChatColor.YELLOW + "Target cleared."); studio.openShotConfig(player, scene.name(), shotId); }
            else player.sendMessage(ChatColor.RED + "Unable to clear target.");
        } else if (slot == 26) studio.openShotConfig(player, scene.name(), shotId);
    }

    private void notifyRange(Player player, Scene scene, String shotId, boolean changed, String edge, long delta) {
        DirectorShot shot = director.plan(scene).get(shotId).orElse(null);
        if (!changed || shot == null) { player.sendMessage(ChatColor.YELLOW + "Cannot move " + edge + " by " + delta + " ticks: the resulting shot would overlap another shot or become invalid."); return; }
        player.sendMessage(ChatColor.GREEN + "Shot " + edge + " moved to " + (edge.equals("start") ? shot.startTick() : shot.endTick()) + ".");
        studio.openShotConfig(player, scene.name(), shotId);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!studio.isInventory(event.getInventory())) return;
        // Do not eagerly discard state: opening a contextual child panel fires a close event for the parent inventory.
    }

    private static boolean usesTarget(DirectorShot.Type type) { return type == DirectorShot.Type.FOLLOW || type == DirectorShot.Type.LOOK_AT || type == DirectorShot.Type.ORBIT; }
    private static boolean usesPath(DirectorShot.Type type) { return type == DirectorShot.Type.DOLLY || type == DirectorShot.Type.RAIL || type == DirectorShot.Type.SPLINE; }
    private static long localTick(DirectorShot shot, long sceneTick) { return Math.max(0, sceneTick - shot.startTick()); }
    private static final class State { private String sceneName; private String selectedShotId; private long timelineTick; private long selectedPathTick = -1; }
}
