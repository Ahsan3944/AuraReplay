package com.ultraop.aurareplay.ui;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorTransform;
import com.ultraop.aurareplay.core.AuraEngine;
import com.ultraop.aurareplay.recording.Recording;
import com.ultraop.aurareplay.recording.RecordingSource;
import com.ultraop.aurareplay.recording.RecordingSourceCatalog;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** In-game browser for turning one recorded entity stream into an editable actor instance. */
public final class ActorSourceBrowser {
    private static final String PREFIX = ChatColor.DARK_AQUA + "AuraReplay / Sources";
    private final AuraEngine engine;
    private final RecordingSourceCatalog catalog = new RecordingSourceCatalog();

    public ActorSourceBrowser(AuraEngine engine) { this.engine = engine; }

    public void openRecordings(Player player) {
        Inventory inventory = new Holder().inventory(27, PREFIX);
        List<Recording> recordings = engine.recordingManager().all().stream().sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList();
        for (int i = 0; i < Math.min(26, recordings.size()); i++) {
            Recording recording = recordings.get(i);
            inventory.setItem(i, item(Material.PAPER, ChatColor.AQUA + recording.name(),
                    ChatColor.GRAY + recording.frames().size() + " frames", ChatColor.GRAY + recording.durationTicks() + " ticks"));
        }
        inventory.setItem(26, item(Material.BARRIER, ChatColor.RED + "Close"));
        player.openInventory(inventory);
    }

    public void openSources(Player player, Recording recording) {
        Inventory inventory = new Holder(recording.name()).inventory(27, PREFIX + " / " + recording.name());
        List<RecordingSource> sources = catalog.sources(recording);
        for (int i = 0; i < Math.min(26, sources.size()); i++) {
            RecordingSource source = sources.get(i);
            inventory.setItem(i, item(Material.ARMOR_STAND, ChatColor.YELLOW + source.displayName(),
                    ChatColor.GRAY + source.type().name(), ChatColor.DARK_GRAY + source.key()));
        }
        inventory.setItem(26, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        player.openInventory(inventory);
    }

    public void createActor(Player player, Recording recording, RecordingSource source) {
        if (!player.hasPermission("aurareplay.actor")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to create AuraReplay actors.");
            return;
        }
        ActorTransform transform = ActorTransform.origin(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
        ActorDefinition actor = engine.actorManager().createFromSource(recording, source, transform);
        player.sendMessage(ChatColor.GREEN + "Actor created: " + ChatColor.WHITE + actor.id());
        player.sendMessage(ChatColor.GRAY + "Source: " + source.key() + " | Recording: " + recording.name());
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    public static final class Holder implements InventoryHolder {
        private final String recordingName;
        private Inventory inventory;
        public Holder() { this(null); }
        public Holder(String recordingName) { this.recordingName = recordingName; }
        public String recordingName() { return recordingName; }
        public Inventory inventory(int size, String title) {
            inventory = org.bukkit.Bukkit.createInventory(this, size, title);
            return inventory;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
