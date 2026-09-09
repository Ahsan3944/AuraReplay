package com.ultraop.aurareplay.nms;

import com.ultraop.aurareplay.camera.CameraBackend;
import com.ultraop.aurareplay.camera.CameraDefinition;
import com.ultraop.aurareplay.camera.CameraTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paper 1.21.11 NMS camera backend.
 *
 * A viewer-local, packet-only invisible marker is used as the client's camera entity.
 * The fake entity is never inserted into a server world, so other viewers do not see it.
 */
public final class NmsCameraBackend implements CameraBackend {
    private final Map<UUID, CameraTarget> targets = new ConcurrentHashMap<>();

    @Override
    public void activate(Player viewer, CameraDefinition camera) {
        CameraTransform transform = camera.transform();
        CameraTarget previous = targets.remove(viewer.getUniqueId());
        if (previous != null) {
            send(viewer, new ClientboundRemoveEntitiesPacket(previous.entity().getId()));
        }

        ArmorStand entity = new ArmorStand(((CraftPlayer) viewer).getHandle().level(),
                transform.x(), transform.y(), transform.z());
        entity.setInvisible(true);
        entity.setNoGravity(true);
        entity.setInvulnerable(true);
        entity.setMarker(true);
        entity.setYRot(transform.yaw());
        entity.setXRot(transform.pitch());
        entity.setYHeadRot(transform.yaw());
        entity.setPos(transform.x(), transform.y(), transform.z());

        targets.put(viewer.getUniqueId(), new CameraTarget(entity));

        send(viewer, new ClientboundAddEntityPacket(
                entity,
                0,
                BlockPos.containing(transform.x(), transform.y(), transform.z())));

        var metadata = entity.getEntityData().getNonDefaultValues();
        if (metadata != null && !metadata.isEmpty()) {
            send(viewer, new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(
                    entity.getId(), metadata));
        }
        send(viewer, new ClientboundSetCameraPacket(entity));
    }

    @Override
    public void update(Player viewer, CameraTransform transform) {
        CameraTarget target = targets.get(viewer.getUniqueId());
        if (target == null) return;

        ArmorStand entity = target.entity();
        entity.setPos(transform.x(), transform.y(), transform.z());
        entity.setYRot(transform.yaw());
        entity.setXRot(transform.pitch());
        entity.setYHeadRot(transform.yaw());

        send(viewer, ClientboundEntityPositionSyncPacket.of(entity));
    }

    @Override
    public void deactivate(Player viewer) {
        CameraTarget target = targets.remove(viewer.getUniqueId());
        if (target != null) {
            send(viewer, new ClientboundRemoveEntitiesPacket(target.entity().getId()));
        }
        send(viewer, new ClientboundSetCameraPacket(((CraftPlayer) viewer).getHandle()));
    }

    private static void send(Player viewer, net.minecraft.network.protocol.Packet<?> packet) {
        ((CraftPlayer) viewer).getHandle().connection.send(packet);
    }

    private record CameraTarget(ArmorStand entity) {}
}
