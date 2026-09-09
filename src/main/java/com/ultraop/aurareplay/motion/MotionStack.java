package com.ultraop.aurareplay.motion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Ordered editable stack of non-destructive motion operations. */
public final class MotionStack {
    private final List<MotionLayer> layers = new ArrayList<>();

    public List<MotionLayer> layers() { return List.copyOf(layers); }
    public int size() { return layers.size(); }
    public boolean isEmpty() { return layers.isEmpty(); }

    public void add(MotionLayer layer) {
        Objects.requireNonNull(layer, "layer");
        if (layers.stream().anyMatch(existing -> existing.id().equals(layer.id())))
            throw new IllegalArgumentException("duplicate motion layer id: " + layer.id());
        layers.add(layer);
    }

    public boolean remove(String id) {
        return layers.removeIf(layer -> layer.id().equals(id));
    }

    public boolean replace(MotionLayer layer) {
        Objects.requireNonNull(layer, "layer");
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i).id().equals(layer.id())) {
                layers.set(i, layer);
                return true;
            }
        }
        return false;
    }

    public boolean setEnabled(String id, boolean enabled) {
        for (int i = 0; i < layers.size(); i++) {
            MotionLayer layer = layers.get(i);
            if (layer.id().equals(id)) {
                layers.set(i, new MotionLayer(layer.id(), layer.type(), layer.x(), layer.y(), layer.z(),
                        layer.yaw(), layer.pitch(), layer.roll(), layer.value(),
                        layer.startTick(), layer.endTick(), enabled));
                return true;
            }
        }
        return false;
    }

    public boolean move(String id, int newIndex) {
        if (newIndex < 0 || newIndex >= layers.size()) return false;
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i).id().equals(id)) {
                MotionLayer layer = layers.remove(i);
                layers.add(newIndex, layer);
                return true;
            }
        }
        return false;
    }

    public void clear() { layers.clear(); }

    public void restore(List<MotionLayer> snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        layers.clear();
        for (MotionLayer layer : snapshot) add(layer);
    }
}
