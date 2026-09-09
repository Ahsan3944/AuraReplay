package com.ultraop.aurareplay.ui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/** Bridges Bukkit inventory events into the viewer-local Studio controller. */
public final class StudioListener implements Listener {
    private final StudioController controller;
    public StudioListener(StudioController controller) { this.controller = controller; }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!controller.isStudioInventory(event.getView().getTopInventory())) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            controller.click(player, event.getRawSlot());
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!controller.isStudioInventory(event.getInventory())) return;
        Bukkit.getScheduler().runTask(controllerPlugin(player), () -> {
            if (!player.isOnline()) {
                controller.close(player);
                return;
            }
            // Inventory navigation calls openInventory() synchronously. By the time
            // this close callback runs, the replacement Studio inventory is normally
            // already open; only close the session when the viewer actually left Studio.
            if (!controller.isStudioInventory(player.getOpenInventory().getTopInventory())) {
                controller.close(player);
            }
        });
    }

    private org.bukkit.plugin.Plugin controllerPlugin(Player player) {
        return player.getServer().getPluginManager().getPlugin("AuraReplay");
    }
}
