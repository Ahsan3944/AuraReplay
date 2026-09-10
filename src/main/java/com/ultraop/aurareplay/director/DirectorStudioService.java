package com.ultraop.aurareplay.director;

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

/** In-game Studio editor for director shots and camera paths. */
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
                    "Camera: " + shot.cameraId(), "Path points: " + shot.path().points().size()));
        }
        inventory.setItem(20, item(Material.CLOCK, "Add Shot", "Creates a 40-tick shot using the first camera"));
        inventory.setItem(21, item(Material.BRUSH, "Add Path Point", "Adds a point at the current preview position"));
        inventory.setItem(22, item(Material.REDSTONE, "Remove Path Point", "Removes the current path point"));
        inventory.setItem(23, item(Material.ENDER_EYE, "Preview Director", "Preview the current scene director"));
        inventory.setItem(24, item(Material.REPEATER, "Next Shot Type", "Cycle STATIC/FOLLOW/LOOK_AT/ORBIT/DOLLY/RAIL/SPLINE"));
        inventory.setItem(25, item(Material.LEVER, "Next Transition", "Cycle CUT/BLEND/FADE"));
        inventory.setItem(26, item(Material.ARROW, "Back"));
        player.openInventory(inventory);
    }

    public boolean isInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof Holder;
    }

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

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + name);
        meta.setLore(List.of(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    public static final class Holder implements org.bukkit.inventory.InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
