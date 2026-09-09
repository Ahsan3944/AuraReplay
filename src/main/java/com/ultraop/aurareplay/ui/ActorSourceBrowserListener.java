package com.ultraop.aurareplay.ui;

import com.ultraop.aurareplay.core.AuraEngine;
import com.ultraop.aurareplay.recording.Recording;
import com.ultraop.aurareplay.recording.RecordingSource;
import com.ultraop.aurareplay.recording.RecordingSourceCatalog;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

/** Handles the recording -> source -> actor flow for ActorSourceBrowser. */
public final class ActorSourceBrowserListener implements Listener {
    private final ActorSourceBrowser browser;
    private final AuraEngine engine;
    private final RecordingSourceCatalog catalog = new RecordingSourceCatalog();

    public ActorSourceBrowserListener(ActorSourceBrowser browser, AuraEngine engine) {
        this.browser = browser;
        this.engine = engine;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof ActorSourceBrowser.Holder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getRawSlot();
        if (holder.recordingName() == null) {
            if (slot == 26) { player.closeInventory(); return; }
            if (slot == 25) { player.sendMessage("Use /aurareplay actor list to manage existing actors."); return; }
            List<Recording> recordings = engine.recordingManager().all().stream()
                    .sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList();
            if (slot >= 0 && slot < recordings.size() && slot < 25) browser.openSources(player, recordings.get(slot));
            return;
        }

        if (slot == 26) { browser.openRecordings(player); return; }
        Recording recording = engine.recordingManager().get(holder.recordingName()).orElse(null);
        if (recording == null) { browser.openRecordings(player); return; }
        List<RecordingSource> sources = catalog.sources(recording);
        if (slot < 0 || slot >= sources.size() || slot >= 26) return;
        browser.createActor(player, recording, sources.get(slot));
    }
}
