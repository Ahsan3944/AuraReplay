package com.ultraop.aurareplay.ui;

import com.ultraop.aurareplay.AuraReplayPlugin;
import com.ultraop.aurareplay.effects.EffectCue;
import com.ultraop.aurareplay.effects.EffectCuePlayer;
import com.ultraop.aurareplay.scene.Scene;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-game editor for scene particle/sound cues. */
public final class EffectsStudioService {
    public static final String TITLE_PREFIX = ChatColor.DARK_AQUA + "Effects / ";
    private final AuraReplayPlugin plugin;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public EffectsStudioService() { this.plugin = AuraReplayPlugin.getPlugin(AuraReplayPlugin.class); }
    public boolean isEffectsInventory(String title) { return title != null && title.startsWith(TITLE_PREFIX); }
    public void open(Player player) { states.put(player.getUniqueId(), new State()); openScenes(player); }

    public void click(Player player, int slot, boolean rightClick) {
        State state = states.get(player.getUniqueId());
        if (state == null) { open(player); return; }
        if (state.sceneName == null) { sceneListClick(player, state, slot); return; }
        Scene scene = scene(state.sceneName);
        if (scene == null) { states.remove(player.getUniqueId()); openScenes(player); return; }
        if (state.cueId == null) {
            if (slot == 21) { add(player, state, EffectCue.Type.PARTICLE); return; }
            if (slot == 22) { add(player, state, EffectCue.Type.SOUND); return; }
            if (slot == 26) { state.sceneName = null; openScenes(player); return; }
            List<EffectCue> cues = scene.effects().all();
            if (slot >= 0 && slot < Math.min(20, cues.size())) { state.cueId = cues.get(slot).id(); openCueEditor(player, state, scene, cues.get(slot)); }
            return;
        }
        cueEditorClick(player, state, scene, slot, rightClick);
    }

    public void close(Player player) { states.remove(player.getUniqueId()); }

    private void openScenes(Player player) {
        Inventory inventory = org.bukkit.Bukkit.createInventory(null, 27, TITLE_PREFIX + "Scenes");
        List<Scene> scenes = sortedScenes();
        for (int i = 0; i < Math.min(20, scenes.size()); i++) {
            Scene scene = scenes.get(i);
            item(inventory, i, Material.FIREWORK_ROCKET, scene.name(), "Cues: " + scene.effects().all().size(), "Duration: " + scene.timeline().durationTicks() + " ticks");
        }
        item(inventory, 26, Material.ARROW, "Back", "Return to Studio");
        player.openInventory(inventory);
    }

    private void sceneListClick(Player player, State state, int slot) {
        if (slot == 26) { player.closeInventory(); return; }
        List<Scene> scenes = sortedScenes();
        if (slot < 0 || slot >= Math.min(20, scenes.size())) return;
        state.sceneName = scenes.get(slot).name();
        state.cueId = null;
        openCues(player, state, scenes.get(slot));
    }

    private void openCues(Player player, State state, Scene scene) {
        state.cueId = null;
        Inventory inventory = org.bukkit.Bukkit.createInventory(null, 27, TITLE_PREFIX + scene.name());
        List<EffectCue> cues = scene.effects().all();
        for (int i = 0; i < Math.min(20, cues.size()); i++) {
            EffectCue cue = cues.get(i);
            item(inventory, i, cue.type() == EffectCue.Type.PARTICLE ? Material.FIREWORK_STAR : Material.NOTE_BLOCK,
                    cue.type().name() + " #" + cue.id(), "Tick: " + cue.tick(), "Key: " + cue.key());
        }
        item(inventory, 21, Material.FIREWORK_STAR, "Add Particle", "Creates a cue at your current position");
        item(inventory, 22, Material.NOTE_BLOCK, "Add Sound", "Creates a cue at your current position");
        item(inventory, 26, Material.ARROW, "Back", "Return to scene list");
        player.openInventory(inventory);
    }

    private void cueEditorClick(Player player, State state, Scene scene, int slot, boolean rightClick) {
        EffectCue cue = scene.effects().all().stream().filter(c -> c.id().equals(state.cueId)).findFirst().orElse(null);
        if (cue == null) { state.cueId = null; openCues(player, state, scene); return; }
        if (slot == 26) { state.cueId = null; openCues(player, state, scene); return; }
        long tick = cue.tick();
        if (slot == 10) tick = Math.max(0, tick - 20);
        else if (slot == 11) tick += 20;
        else if (slot == 13) { scene.effects().remove(cue.id()); state.cueId = null; player.sendMessage(ChatColor.YELLOW + "Effect cue deleted."); openCues(player, state, scene); return; }
        else if (slot == 14) { EffectCuePlayer.play(player, cue); player.sendMessage(ChatColor.GREEN + "Effect previewed."); return; }
        else return;
        EffectCue updated = new EffectCue(cue.id(), tick, cue.type(), cue.x(), cue.y(), cue.z(), cue.key(), cue.volume(), cue.pitch(), cue.count(), cue.spread());
        scene.effects().replace(updated);
        openCueEditor(player, state, scene, updated);
    }

    private void openCueEditor(Player player, State state, Scene scene, EffectCue cue) {
        state.cueId = cue.id();
        Inventory inventory = org.bukkit.Bukkit.createInventory(null, 27, TITLE_PREFIX + "Cue " + cue.id());
        item(inventory, 10, Material.REDSTONE, "Tick -20", "Current: " + cue.tick());
        item(inventory, 11, Material.EMERALD, "Tick +20", "Current: " + cue.tick());
        item(inventory, 13, Material.BARRIER, "Delete Cue", "Remove this cue");
        item(inventory, 14, Material.NOTE_BLOCK, "Preview", cue.type().name(), cue.key());
        item(inventory, 18, Material.COMPASS, "Type", cue.type().name());
        item(inventory, 19, Material.NAME_TAG, "Key", cue.key());
        item(inventory, 20, Material.CLOCK, "Timeline Tick", Long.toString(cue.tick()));
        item(inventory, 21, Material.ARMOR_STAND, "Position", fmt(cue.x()) + ", " + fmt(cue.y()) + ", " + fmt(cue.z()));
        item(inventory, 26, Material.ARROW, "Back", "Return to cue list");
        player.openInventory(inventory);
    }

    private void add(Player player, State state, EffectCue.Type type) {
        Scene scene = scene(state.sceneName);
        if (scene == null) return;
        var loc = player.getLocation();
        String id = "cue-" + UUID.randomUUID().toString().substring(0, 8);
        EffectCue cue = type == EffectCue.Type.PARTICLE
                ? EffectCue.particle(id, 0, loc.getX(), loc.getY(), loc.getZ(), "FLAME", 12, .15)
                : EffectCue.sound(id, 0, loc.getX(), loc.getY(), loc.getZ(), "ENTITY_PLAYER_LEVELUP", 1f, 1f);
        scene.effects().add(cue);
        state.cueId = cue.id();
        openCueEditor(player, state, scene, cue);
    }

    private List<Scene> sortedScenes() {
        List<Scene> scenes = new ArrayList<>(plugin.engine().sceneManager().all());
        scenes.sort(Comparator.comparing(Scene::name, String.CASE_INSENSITIVE_ORDER));
        return scenes;
    }

    private Scene scene(String name) { return name == null ? null : plugin.engine().sceneManager().get(name).orElse(null); }
    private void item(Inventory inventory, int slot, Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + name);
        meta.setLore(java.util.Arrays.stream(lore).map(s -> ChatColor.GRAY + s).toList());
        stack.setItemMeta(meta); inventory.setItem(slot, stack);
    }
    private String fmt(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }
    private static final class State { private String sceneName; private String cueId; }
}
