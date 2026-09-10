package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorManager;
import com.ultraop.aurareplay.camera.CameraDefinition;
import com.ultraop.aurareplay.camera.CameraManager;
import com.ultraop.aurareplay.camera.CameraTransform;
import com.ultraop.aurareplay.scene.Scene;
import com.ultraop.aurareplay.scene.SceneManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** In-game Studio editor for director shots, cameras, targets and camera paths. */
public final class DirectorStudioService {
    private static final String DIRECTOR_TITLE = ChatColor.DARK_AQUA + "Director / ";
    private static final String CAMERA_TITLE = ChatColor.DARK_BLUE + "Shot Camera / ";
    private static final String TARGET_TITLE = ChatColor.DARK_GREEN + "Shot Target / ";
    private static final String CONFIG_TITLE = ChatColor.DARK_PURPLE + "Shot Config / ";
    private static final String PATH_TITLE = ChatColor.DARK_RED + "Shot Path / ";

    private final SceneManager scenes;
    private final CameraManager cameras;
    private final ActorManager actors;
    private final DirectorController director;

    public DirectorStudioService(SceneManager scenes, CameraManager cameras, ActorManager actors, DirectorController director) {
        this.scenes = Objects.requireNonNull(scenes, "scenes");
        this.cameras = Objects.requireNonNull(cameras, "cameras");
        this.actors = Objects.requireNonNull(actors, "actors");
        this.director = Objects.requireNonNull(director, "director");
    }

    public void open(Player player, String sceneName) {
        Scene scene = scenes.get(sceneName).orElse(null);
        if (scene == null) { player.sendMessage(ChatColor.RED + "Scene not found: " + sceneName); return; }
        DirectorPlan plan = director.plan(scene);
        Inventory inventory = Bukkit.createInventory(new Holder(Mode.DIRECTOR, scene.name(), null), 27, DIRECTOR_TITLE + scene.name());
        List<DirectorShot> shots = plan.shots();
        for (int i = 0; i < Math.min(18, shots.size()); i++) {
            DirectorShot shot = shots.get(i);
            inventory.setItem(i, item(Material.SPYGLASS, shot.id(), "Range: " + shot.startTick() + " - " + shot.endTick(), "Type: " + shot.type(), "Transition: " + shot.transition(), "Camera: " + shot.cameraId(), "Target: " + displayTarget(shot), "Path points: " + shot.path().points().size()));
        }
        boolean playing = director.playing(player);
        inventory.setItem(18, item(Material.ARROW, "Timeline -20", "Move cursor back 20 ticks"));
        inventory.setItem(19, item(Material.ARROW, "Timeline +20", "Move cursor forward 20 ticks"));
        inventory.setItem(20, item(Material.CLOCK, "Add Shot", "Create a new 40-tick shot"));
        inventory.setItem(21, item(Material.BRUSH, "Add Path Point", "Capture your current position and rotation"));
        inventory.setItem(22, item(Material.REDSTONE, "Remove Path Point", "Remove the point at the current local tick"));
        inventory.setItem(23, item(playing ? Material.PAUSE : Material.PLAY, playing ? "Pause Director" : "Play Director", playing ? "Pause at the current playback tick" : "Play from the current timeline tick"));
        inventory.setItem(24, item(Material.ENDER_EYE, "Preview Director", "Render the current director cursor without starting playback"));
        inventory.setItem(25, item(Material.BARRIER, "Stop Director", "Stop playback and restore the normal camera"));
        inventory.setItem(26, item(Material.ARROW, "Close Director"));
        player.openInventory(inventory);
    }

    public void openShotConfig(Player player, String sceneName, String shotId) {
        Scene scene = scenes.get(sceneName).orElse(null);
        DirectorShot shot = scene == null ? null : director.plan(scene).get(shotId).orElse(null);
        if (scene == null || shot == null) { player.sendMessage(ChatColor.RED + "Shot is no longer available."); return; }
        Inventory inventory = Bukkit.createInventory(new Holder(Mode.CONFIG, scene.name(), shot.id()), 27, CONFIG_TITLE + scene.name());
        inventory.setItem(4, item(Material.SPYGLASS, "Shot " + shot.id(), "Configure this director shot"));
        inventory.setItem(9, item(Material.REPEATER, "Start -20", "Current: " + shot.startTick()));
        inventory.setItem(10, item(Material.REPEATER, "Start +20", "Current: " + shot.startTick()));
        inventory.setItem(11, item(Material.COMPARATOR, "End -20", "Current: " + shot.endTick()));
        inventory.setItem(12, item(Material.COMPARATOR, "End +20", "Current: " + shot.endTick()));
        inventory.setItem(13, item(Material.SPYGLASS, "Camera", "Current: " + shot.cameraId(), "Open camera picker"));
        if (usesTarget(shot.type())) inventory.setItem(14, item(Material.PLAYER_HEAD, "Target", "Current: " + displayTarget(shot), "Open actor target picker"));
        inventory.setItem(15, item(Material.REPEATER, "Shot Type", "Current: " + shot.type(), "Cycle shot type"));
        inventory.setItem(16, item(Material.LEVER, "Transition", "Current: " + shot.transition(), "Cycle transition"));
        if (usesPath(shot.type())) inventory.setItem(17, item(Material.BRUSH, "Path", "Points: " + shot.path().points().size(), "Open contextual path editor"));
        inventory.setItem(22, item(Material.ARROW, "Back to Director", "Return to shot list"));
        inventory.setItem(26, item(Material.BARRIER, "Close", "Close Director Studio"));
        player.openInventory(inventory);
    }

    public void openCameraPicker(Player player, String sceneName, String shotId) {
        Scene scene = scenes.get(sceneName).orElse(null);
        DirectorShot shot = scene == null ? null : director.plan(scene).get(shotId).orElse(null);
        if (scene == null || shot == null) { player.sendMessage(ChatColor.RED + "Shot is no longer available."); return; }
        Inventory inventory = Bukkit.createInventory(new Holder(Mode.CAMERA, scene.name(), shot.id()), 27, CAMERA_TITLE + scene.name());
        List<CameraDefinition> list = cameras();
        for (int i = 0; i < Math.min(18, list.size()); i++) {
            CameraDefinition camera = list.get(i);
            boolean current = camera.id().equalsIgnoreCase(shot.cameraId());
            inventory.setItem(i, item(current ? Material.LIME_DYE : Material.SPYGLASS, (current ? "[CURRENT] " : "") + camera.id(), "Name: " + camera.name(), current ? "Currently assigned" : "Click to assign"));
        }
        inventory.setItem(18, item(Material.ARROW, "Back to Shot", "Return to shot configuration"));
        if (list.size() > 18) inventory.setItem(19, item(Material.PAPER, "Showing first 18 cameras", "Camera picker is intentionally contextual and compact"));
        inventory.setItem(26, item(Material.BARRIER, "Close", "Close Director Studio"));
        player.openInventory(inventory);
    }

    public void openTargetPicker(Player player, String sceneName, String shotId) {
        Scene scene = scenes.get(sceneName).orElse(null);
        DirectorShot shot = scene == null ? null : director.plan(scene).get(shotId).orElse(null);
        if (scene == null || shot == null) { player.sendMessage(ChatColor.RED + "Shot is no longer available."); return; }
        Inventory inventory = Bukkit.createInventory(new Holder(Mode.TARGET, scene.name(), shot.id()), 27, TARGET_TITLE + scene.name());
        List<ActorDefinition> list = actors(scene);
        for (int i = 0; i < Math.min(18, list.size()); i++) {
            ActorDefinition actor = list.get(i);
            String id = actor.id().toString();
            boolean current = id.equalsIgnoreCase(shot.targetActorId());
            inventory.setItem(i, item(current ? Material.LIME_DYE : Material.PLAYER_HEAD, (current ? "[CURRENT] " : "") + actor.name(), "Actor: " + id, current ? "Currently targeted" : "Click to assign"));
        }
        inventory.setItem(18, item(Material.BARRIER, "Clear Target", "Remove the target actor from this shot"));
        inventory.setItem(19, item(Material.PAPER, "Scene Actors: " + list.size(), "Only actors included in this scene are selectable"));
        inventory.setItem(26, item(Material.ARROW, "Back to Shot", "Return to shot configuration"));
        player.openInventory(inventory);
    }

    public void openPathEditor(Player player, String sceneName, String shotId) {
        Scene scene = scenes.get(sceneName).orElse(null);
        DirectorShot shot = scene == null ? null : director.plan(scene).get(shotId).orElse(null);
        if (scene == null || shot == null) { player.sendMessage(ChatColor.RED + "Shot is no longer available."); return; }
        if (!usesPath(shot.type())) { player.sendMessage(ChatColor.YELLOW + "This shot type does not use a path."); openShotConfig(player, scene.name(), shot.id()); return; }
        Inventory inventory = Bukkit.createInventory(new Holder(Mode.PATH, scene.name(), shot.id()), 27, PATH_TITLE + scene.name());
        List<DirectorPathEvaluator.PathPoint> points = shot.path().points();
        for (int i = 0; i < Math.min(18, points.size()); i++) {
            DirectorPathEvaluator.PathPoint point = points.get(i);
            inventory.setItem(i, item(Material.BRUSH, "Point " + point.tick(), "Tick: " + point.tick(), "Position: " + format(point.transform()), "Click to select this point"));
        }
        inventory.setItem(18, item(Material.ARROW, "Cursor -20", "Move path cursor back 20 ticks"));
        inventory.setItem(19, item(Material.ARROW, "Cursor +20", "Move path cursor forward 20 ticks"));
        inventory.setItem(20, item(Material.BRUSH, "Capture at Cursor", "Add or replace a point at the current cursor tick"));
        inventory.setItem(21, item(Material.COMPASS, "Recapture Selected", "Replace the selected point with your current position and rotation"));
        inventory.setItem(22, item(Material.REDSTONE, "Move Selected -20", "Move the selected point 20 ticks earlier"));
        inventory.setItem(23, item(Material.REDSTONE, "Move Selected +20", "Move the selected point 20 ticks later"));
        inventory.setItem(24, item(Material.TNT, "Remove Selected", "Delete the selected path point"));
        inventory.setItem(25, item(Material.BARRIER, "Clear Path", "Remove all path points from this shot"));
        inventory.setItem(26, item(Material.ARROW, "Back to Shot", "Return to shot configuration"));
        player.openInventory(inventory);
    }

    public boolean isInventory(Inventory inventory) { return inventory != null && inventory.getHolder() instanceof Holder; }
    public void close(Player player) { player.closeInventory(); }
    public List<CameraDefinition> cameras() { return cameras.all().stream().sorted(Comparator.comparing(CameraDefinition::id)).toList(); }
    public List<ActorDefinition> actors(Scene scene) { return scene.actorIds().stream().map(id -> actors.get(id).orElse(null)).filter(Objects::nonNull).sorted(Comparator.comparing(a -> a.id().toString())).toList(); }

    public boolean addShot(Scene scene) {
        List<CameraDefinition> list = cameras();
        if (list.isEmpty()) return false;
        DirectorPlan plan = director.plan(scene);
        long start = plan.durationTicks();
        plan.add(new DirectorShot("shot-" + (plan.shots().size() + 1), start, start + 40, list.get(0).id(), null, DirectorShot.Type.STATIC, DirectorShot.Transition.CUT));
        return true;
    }

    public boolean addPathPoint(Scene scene, String shotId, long tick, CameraTransform transform) {
        DirectorShot shot = director.plan(scene).get(shotId).orElse(null);
        if (shot == null || !usesPath(shot.type())) return false;
        shot.path().setPoint(tick, transform);
        return true;
    }

    public boolean removePathPoint(Scene scene, String shotId, long tick) {
        DirectorShot shot = director.plan(scene).get(shotId).orElse(null);
        return shot != null && shot.path().removePoint(tick);
    }

    public boolean clearPath(Scene scene, String shotId) {
        DirectorShot shot = director.plan(scene).get(shotId).orElse(null);
        if (shot == null || !usesPath(shot.type())) return false;
        shot.path().clear();
        return true;
    }

    public boolean movePathPoint(Scene scene, String shotId, long fromTick, long toTick) {
        DirectorShot shot = director.plan(scene).get(shotId).orElse(null);
        if (shot == null || !usesPath(shot.type()) || fromTick < 0 || toTick < 0) return false;
        DirectorPathEvaluator.PathPoint selected = shot.path().points().stream().filter(point -> point.tick() == fromTick).findFirst().orElse(null);
        if (selected == null) return false;
        if (fromTick != toTick && shot.path().points().stream().anyMatch(point -> point.tick() == toTick)) return false;
        shot.path().setPoint(toTick, selected.transform());
        if (fromTick != toTick) shot.path().removePoint(fromTick);
        return true;
    }

    public boolean setCamera(Scene scene, String shotId, String cameraId) {
        DirectorShot shot = director.plan(scene).get(shotId).orElse(null);
        if (shot == null || cameraId == null || cameraId.isBlank() || cameras.get(cameraId).isEmpty()) return false;
        return replace(director.plan(scene), shot, new DirectorShot(shot.id(), shot.startTick(), shot.endTick(), cameraId, shot.targetActorId(), shot.type(), shot.transition(), shot.path()));
    }

    public boolean setTarget(Scene scene, String shotId, String actorId) {
        DirectorShot shot = director.plan(scene).get(shotId).orElse(null);
        if (shot == null) return false;
        if (actorId != null) {
            UUID uuid;
            try { uuid = UUID.fromString(actorId); } catch (IllegalArgumentException ex) { return false; }
            if (!scene.actorIds().contains(new com.ultraop.aurareplay.actor.ActorId(uuid))) return false;
        }
        return replace(director.plan(scene), shot, new DirectorShot(shot.id(), shot.startTick(), shot.endTick(), shot.cameraId(), actorId, shot.type(), shot.transition(), shot.path()));
    }

    public boolean clearTarget(Scene scene, String shotId) { return setTarget(scene, shotId, null); }

    public boolean adjustStart(Scene scene, String shotId, long delta) {
        DirectorShot shot = director.plan(scene).get(shotId).orElse(null);
        if (shot == null) return false;
        long start = Math.max(0L, shot.startTick() + delta);
        if (start >= shot.endTick()) return false;
        return replace(director.plan(scene), shot, new DirectorShot(shot.id(), start, shot.endTick(), shot.cameraId(), shot.targetActorId(), shot.type(), shot.transition(), shot.path()));
    }

    public boolean adjustEnd(Scene scene, String shotId, long delta) {
        DirectorShot shot = director.plan(scene).get(shotId).orElse(null);
        if (shot == null) return false;
        long end = Math.max(shot.startTick() + 1L, shot.endTick() + delta);
        return replace(director.plan(scene), shot, new DirectorShot(shot.id(), shot.startTick(), end, shot.cameraId(), shot.targetActorId(), shot.type(), shot.transition(), shot.path()));
    }

    private static boolean usesTarget(DirectorShot.Type type) { return type == DirectorShot.Type.FOLLOW || type == DirectorShot.Type.LOOK_AT || type == DirectorShot.Type.ORBIT; }
    private static boolean usesPath(DirectorShot.Type type) { return type == DirectorShot.Type.DOLLY || type == DirectorShot.Type.RAIL || type == DirectorShot.Type.SPLINE; }

    private static boolean replace(DirectorPlan plan, DirectorShot oldShot, DirectorShot replacement) {
        plan.remove(oldShot.id());
        try { plan.add(replacement); return true; } catch (RuntimeException ex) { plan.add(oldShot); return false; }
    }

    private static String displayTarget(DirectorShot shot) { return shot.targetActorId() == null ? "none" : shot.targetActorId(); }

    private static String format(CameraTransform transform) {
        return String.format("%.1f, %.1f, %.1f", transform.x(), transform.y(), transform.z());
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + name);
            meta.setLore(List.of(lore).stream().map(line -> ChatColor.GRAY + line).toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public enum Mode { DIRECTOR, CONFIG, CAMERA, TARGET, PATH }

    public static final class Holder implements org.bukkit.inventory.InventoryHolder {
        private final Mode mode;
        private final String sceneName;
        private final String shotId;
        public Holder(Mode mode, String sceneName, String shotId) { this.mode = mode; this.sceneName = sceneName; this.shotId = shotId; }
        public Mode mode() { return mode; }
        public String sceneName() { return sceneName; }
        public String shotId() { return shotId; }
        @Override public Inventory getInventory() { return null; }
    }
}
