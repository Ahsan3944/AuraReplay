package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.camera.CameraDefinition;
import com.ultraop.aurareplay.camera.CameraManager;
import com.ultraop.aurareplay.camera.CameraTransform;
import com.ultraop.aurareplay.scene.Scene;
import com.ultraop.aurareplay.scene.SceneManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** In-game Studio editor for director shots, cameras, targets and camera paths. */
public final class DirectorStudioService {
    private final SceneManager scenes;
    private final CameraManager cameras;
    private final DirectorController director;

    public DirectorStudioService(SceneManager scenes, CameraManager cameras, DirectorController director) {
        this.scenes = Objects.requireNonNull(scenes, "scenes");
        this.cameras = Objects.requireNonNull(cameras, "cameras");
        this.director = Objects.requireNonNull(director, "director");
    }

    public void open(Player player, String sceneName) {
        Scene scene = scenes.get(sceneName).orElse(null);
        if (scene == null) { player.sendMessage(ChatColor.RED + "Scene not found: " + sceneName); return; }
        DirectorPlan plan = director.plan(scene);
        Inventory inventory = Bukkit.createInventory(new Holder(), 27, ChatColor.DARK_AQUA + "Director / " + scene.name());
        List<DirectorShot> shots = plan.shots();
        for (int i = 0; i < Math.min(18, shots.size()); i++) {
            DirectorShot shot = shots.get(i);
            inventory.setItem(i, item(Material.SPYGLASS, shot.id(),
                    "Range: " + shot.startTick() + " - " + shot.endTick(),
                    "Type: " + shot.type(), "Transition: " + shot.transition(),
                    "Camera: " + shot.cameraId(), "Target: " + displayTarget(shot),
                    "Path points: " + shot.path().points().size()));
        }
        inventory.setItem(18, item(Material.ARROW, "Timeline -20", "Move director cursor back 20 ticks"));
        inventory.setItem(19, item(Material.ARROW, "Timeline +20", "Move director cursor forward 20 ticks"));
        inventory.setItem(20, item(Material.CLOCK, "Add Shot", "Create a new 40-tick shot"));
        inventory.setItem(21, item(Material.BRUSH, "Add Path Point", "Capture your current position and rotation"));
        inventory.setItem(22, item(Material.REDSTONE, "Remove Path Point", "Remove the point at the current local tick"));
        inventory.setItem(23, item(Material.ENDER_EYE, "Preview Director", "Preview the current director cursor"));
        inventory.setItem(24, item(Material.REPEATER, "Next Shot Type", "Cycle STATIC/FOLLOW/LOOK_AT/ORBIT/DOLLY/RAIL/SPLINE"));
        inventory.setItem(25, item(Material.LEVER, "Next Transition", "Cycle CUT/BLEND/FADE"));
        inventory.setItem(26, item(Material.ARROW, "Close Director"));
        player.openInventory(inventory);
    }

    public boolean isInventory(Inventory inventory) { return inventory != null && inventory.getHolder() instanceof Holder; }
    public void close(Player player) { player.closeInventory(); }

    public boolean addShot(Scene scene) {
        List<CameraDefinition> list = cameras.all().stream().sorted(Comparator.comparing(CameraDefinition::id)).toList();
        if (list.isEmpty()) return false;
        DirectorPlan plan = director.plan(scene);
        long start = plan.durationTicks();
        long end = start + 40;
        String id = "shot-" + (plan.shots().size() + 1);
        plan.add(new DirectorShot(id, start, end, list.get(0).id(), null, DirectorShot.Type.STATIC, DirectorShot.Transition.CUT));
        return true;
    }

    public boolean addPathPoint(Scene scene, String shotId, long tick, CameraTransform transform) {
        DirectorShot shot = director.plan(scene).get(shotId).orElse(null);
        if (shot == null) return false;
        shot.path().setPoint(tick, transform);
        return true;
    }

    public boolean removePathPoint(Scene scene, String shotId, long tick) {
        DirectorShot shot = director.plan(scene).get(shotId).orElse(null);
        return shot != null && shot.path().removePoint(tick);
    }

    public boolean setCamera(Scene scene, String shotId, String cameraId) {
        DirectorShot shot = director.plan(scene).get(shotId).orElse(null);
        if (shot == null || cameras.get(cameraId).isEmpty()) return false;
        return replace(director.plan(scene), shot, new DirectorShot(shot.id(), shot.startTick(), shot.endTick(), cameraId, shot.targetActorId(), shot.type(), shot.transition(), shot.path()));
    }

    public boolean clearTarget(Scene scene, String shotId) {
        return setTarget(scene, shotId, null);
    }

    public boolean setTarget(Scene scene, String shotId, String actorId) {
        DirectorShot shot = director.plan(scene).get(shotId).orElse(null);
        if (shot == null) return false;
        if (actorId != null) {
            try { if (directorActor(scene, actorId) == null) return false; }
            catch (IllegalArgumentException ignored) { return false; }
        }
        return replace(director.plan(scene), shot, new DirectorShot(shot.id(), shot.startTick(), shot.endTick(), shot.cameraId(), actorId, shot.type(), shot.transition(), shot.path()));
    }

    public List<CameraDefinition> cameras() { return cameras.all().stream().sorted(Comparator.comparing(CameraDefinition::id)).toList(); }
    public List<ActorDefinition> actors(Scene scene) { return scene.actorIds().stream().map(id -> findActor(id)).filter(Objects::nonNull).sorted(Comparator.comparing(a -> a.id().toString())).toList(); }

    private ActorDefinition findActor(java.util.UUID id) { return null; }
    private ActorDefinition directorActor(Scene scene, String actorId) {
        java.util.UUID uuid = java.util.UUID.fromString(actorId);
        return scene.actorIds().contains(new com.ultraop.aurareplay.actor.ActorId(uuid)) ? new ActorDefinition(new com.ultraop.aurareplay.actor.ActorId(uuid), "actor") : null;
    }

    private static boolean replace(DirectorPlan plan, DirectorShot oldShot, DirectorShot replacement) {
        plan.remove(oldShot.id());
        try { plan.add(replacement); return true; } catch (RuntimeException ex) { plan.add(oldShot); return false; }
    }

    private static String displayTarget(DirectorShot shot) { return shot.targetActorId() == null ? "none" : shot.targetActorId(); }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + name);
        meta.setLore(List.of(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    public static final class Holder implements org.bukkit.inventory.InventoryHolder { @Override public Inventory getInventory() { return null; } }
}
