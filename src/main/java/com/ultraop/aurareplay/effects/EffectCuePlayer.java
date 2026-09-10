package com.ultraop.aurareplay.effects;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Main-thread renderer for viewer-local effect cues. */
public final class EffectCuePlayer {
    private EffectCuePlayer() { }

    public static void play(Player viewer, EffectCue cue) {
        if (viewer == null || cue == null) return;
        if (cue.type() == EffectCue.Type.SOUND) playSound(viewer, cue);
        else playParticle(viewer, cue);
    }

    private static void playSound(Player viewer, EffectCue cue) {
        Sound sound;
        try { sound = Sound.valueOf(cue.key().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return; }
        viewer.playSound(viewer.getLocation().clone().setDirection(viewer.getLocation().getDirection()), sound, cue.volume(), cue.pitch());
    }

    private static void playParticle(Player viewer, EffectCue cue) {
        Particle particle;
        try { particle = Particle.valueOf(cue.key().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return; }
        viewer.spawnParticle(particle, cue.x(), cue.y(), cue.z(), cue.count(), cue.spread(), cue.spread(), cue.spread(), 0d);
    }
}
