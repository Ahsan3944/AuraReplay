package com.ultraop.aurareplay.listener;

import com.ultraop.aurareplay.actor.ActorAction;
import com.ultraop.aurareplay.recording.TickRecorder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;

/** Captures sparse player animation/state events into the active tick recording. */
public final class ActorActionCaptureListener implements Listener {
    private final TickRecorder recorder;
    public ActorActionCaptureListener(TickRecorder recorder){this.recorder=recorder;}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onAnimation(PlayerAnimationEvent event){recorder.recordAction(event.getPlayer(),ActorAction.SWING);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onDamage(EntityDamageEvent event){if(event.getEntity() instanceof Player player)recorder.recordAction(player,ActorAction.HURT);}
    @EventHandler(priority=EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event){if(event.getEntity() instanceof Player player)recorder.recordAction(player,ActorAction.DEATH);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onSneak(PlayerToggleSneakEvent event){recorder.recordAction(event.getPlayer(),event.isSneaking()?ActorAction.START_SNEAK:ActorAction.STOP_SNEAK);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onSprint(PlayerToggleSprintEvent event){recorder.recordAction(event.getPlayer(),event.isSprinting()?ActorAction.START_SPRINT:ActorAction.STOP_SPRINT);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onGlide(EntityToggleGlideEvent event){if(event.getEntity() instanceof Player player)recorder.recordAction(player,event.isGliding()?ActorAction.START_GLIDE:ActorAction.STOP_GLIDE);}
}
