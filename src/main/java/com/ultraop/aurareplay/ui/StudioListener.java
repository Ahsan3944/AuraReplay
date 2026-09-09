package com.ultraop.aurareplay.ui;

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
        if (controller.session(player) == null) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == event.getView().getTopInventory()) controller.click(player, event.getRawSlot());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && controller.session(player) != null) controller.close(player);
    }
}
