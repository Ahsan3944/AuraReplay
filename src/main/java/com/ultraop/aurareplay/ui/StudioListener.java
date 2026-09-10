package com.ultraop.aurareplay.ui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/** Bridges Bukkit inventory events into the viewer-local Studio controller. */
public final class StudioListener implements Listener {
    private final StudioController controller;
    private final ActorSourceBrowser sourceBrowser;
    private final MotionStudioService motionStudio;
    private final AppearanceStudioService appearanceStudio;

    public StudioListener(StudioController controller, ActorSourceBrowser sourceBrowser) {
        this.controller=controller;this.sourceBrowser=sourceBrowser;this.motionStudio=new MotionStudioService(controller);this.appearanceStudio=new AppearanceStudioService(controller);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if(!(event.getWhoClicked() instanceof Player player))return;if(!controller.isStudioInventory(event.getView().getTopInventory()))return;event.setCancelled(true);if(event.getClickedInventory()!=event.getView().getTopInventory())return;
        StudioSession session=controller.session(player);
        if(session!=null&&session.page()==StudioSession.Page.EQUIPMENT){appearanceStudio.click(player,event.getRawSlot(),event.isRightClick());return;}
        if(event.getRawSlot()==10&&event.getView().getTitle().contains("AuraReplay Studio")){sourceBrowser.openRecordings(player);return;}
        if(session!=null&&session.page()==StudioSession.Page.MOTION){motionStudio.click(player,event.getRawSlot());return;}
        controller.click(player,event.getRawSlot());session=controller.session(player);
        if(session!=null&&session.page()==StudioSession.Page.MOTION)motionStudio.open(player);else if(session!=null&&session.page()==StudioSession.Page.EQUIPMENT)appearanceStudio.open(player);
    }
    @EventHandler
    public void onClose(InventoryCloseEvent event){if(!(event.getPlayer() instanceof Player player))return;if(!controller.isStudioInventory(event.getInventory()))return;motionStudio.close(player);Bukkit.getScheduler().runTask(controllerPlugin(player),()->{if(!player.isOnline()){controller.close(player);return;}if(!controller.isStudioInventory(player.getOpenInventory().getTopInventory()))controller.close(player);});}
    private org.bukkit.plugin.Plugin controllerPlugin(Player player){return player.getServer().getPluginManager().getPlugin("AuraReplay");}
}
