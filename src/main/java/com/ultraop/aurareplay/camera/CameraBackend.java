package com.ultraop.aurareplay.camera;

import org.bukkit.entity.Player;

/** Version-isolated boundary for changing a viewer's client camera. */
public interface CameraBackend {
    void activate(Player viewer, CameraDefinition camera);
    void update(Player viewer, CameraTransform transform);
    void deactivate(Player viewer);
}
