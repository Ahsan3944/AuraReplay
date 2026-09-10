package com.ultraop.aurareplay.effects;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Ordered, editable collection of timeline effect cues. */
public final class EffectCueTrack {
    private final List<EffectCue> cues = new ArrayList<>();

    public boolean add(EffectCue cue) {
        Objects.requireNonNull(cue, "cue");
        if (cues.stream().anyMatch(existing -> existing.id().equals(cue.id()))) return false;
        cues.add(cue);
        cues.sort(Comparator.comparingLong(EffectCue::tick).thenComparing(EffectCue::id));
        return true;
    }

    public boolean remove(String id) {
        return cues.removeIf(cue -> cue.id().equals(id));
    }

    public boolean replace(EffectCue cue) {
        Objects.requireNonNull(cue, "cue");
        for (int i = 0; i < cues.size(); i++) {
            if (cues.get(i).id().equals(cue.id())) {
                cues.set(i, cue);
                cues.sort(Comparator.comparingLong(EffectCue::tick).thenComparing(EffectCue::id));
                return true;
            }
        }
        return false;
    }

    public List<EffectCue> all() { return List.copyOf(cues); }

    public List<EffectCue> at(long tick) {
        return cues.stream().filter(cue -> cue.tick() == tick).toList();
    }

    public void clear() { cues.clear(); }

    public void restore(List<EffectCue> values) {
        cues.clear();
        if (values != null) values.forEach(this::add);
    }
}
