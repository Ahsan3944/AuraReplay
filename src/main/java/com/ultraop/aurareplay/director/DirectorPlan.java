package com.ultraop.aurareplay.director;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Ordered, deterministic collection of director shots. */
public final class DirectorPlan {
    private final List<DirectorShot> shots = new ArrayList<>();

    public List<DirectorShot> shots() { return List.copyOf(shots); }
    public Optional<DirectorShot> get(String id) { return shots.stream().filter(s -> s.id().equals(id)).findFirst(); }

    public void add(DirectorShot shot) {
        Objects.requireNonNull(shot, "shot");
        if (get(shot.id()).isPresent()) throw new IllegalArgumentException("shot already exists: " + shot.id());
        if (shots.stream().anyMatch(shot::overlaps)) throw new IllegalArgumentException("shot overlaps an existing shot: " + shot.id());
        shots.add(shot);
        shots.sort(Comparator.comparingLong(DirectorShot::startTick).thenComparing(DirectorShot::id));
    }

    public boolean remove(String id) { return shots.removeIf(s -> s.id().equals(id)); }

    /** Returns the shot active at the supplied scene tick. */
    public Optional<DirectorShot> at(double tick) {
        if (!Double.isFinite(tick)) throw new IllegalArgumentException("tick must be finite");
        return shots.stream().filter(s -> s.contains(tick)).findFirst();
    }

    public Optional<DirectorShot> nextAfter(long tick) {
        return shots.stream().filter(s -> s.startTick() > tick).findFirst();
    }

    public Optional<DirectorShot> previousBefore(long tick) {
        DirectorShot result = null;
        for (DirectorShot shot : shots) {
            if (shot.endTick() <= tick) result = shot;
            else break;
        }
        return Optional.ofNullable(result);
    }

    public boolean hasGaps() {
        for (int i = 1; i < shots.size(); i++) if (shots.get(i - 1).endTick() < shots.get(i).startTick()) return true;
        return false;
    }

    public long durationTicks() { return shots.stream().mapToLong(DirectorShot::endTick).max().orElse(0L); }
    public void clear() { shots.clear(); }
}
