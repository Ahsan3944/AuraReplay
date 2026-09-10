package com.ultraop.aurareplay.listener;

import com.ultraop.aurareplay.core.AuraEngine;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/** Cleans viewer-local virtual playback and Studio state when a player leaves. */
public final class PlaybackLifecycleListener implements Listener {
    private final AuraEngine engine;

    public PlaybackLifecycleListener(AuraEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        engine.directorRealtimeRenderManager().stop(event.getPlayer());
        engine.actorPlaybackController().stopAll(event.getPlayer());
        engine.cameraController().stop(event.getPlayer());
        engine.studioController().close(event.getPlayer());
        engine.directorStudioService().close(event.getPlayer());
    }
}
