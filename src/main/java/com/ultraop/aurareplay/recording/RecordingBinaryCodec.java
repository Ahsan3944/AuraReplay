package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.recording.action.Action;
import com.ultraop.aurareplay.recording.snapshot.EntityIdentitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.EntitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.EquipmentSnapshot;
import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;
import org.bukkit.entity.EntityType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Versioned compressed binary codec for immutable recordings. Bukkit is only touched for EntityType lookup. */
public final class RecordingBinaryCodec {
    private static final int MAGIC = 0x41555241; // AURA
    private static final int VERSION = 1;

    public byte[] encode(Recording recording) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(buffer); DataOutputStream out = new DataOutputStream(gzip)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            writeString(out, recording.name());
            out.writeLong(recording.durationTicks());
            out.writeInt(recording.frames().size());
            for (TickSnapshot frame : recording.frames()) writeFrame(out, frame);
        }
        return buffer.toByteArray();
    }

    public Recording decode(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))) {
            if (in.readInt() != MAGIC) throw new IOException("invalid AuraReplay recording header");
            if (in.readInt() != VERSION) throw new IOException("unsupported AuraReplay recording version");
            String name = readString(in);
            long duration = in.readLong();
            int frameCount = checkedCount(in.readInt());
            List<TickSnapshot> frames = new ArrayList<>(frameCount);
            for (int i = 0; i < frameCount; i++) frames.add(readFrame(in));
            return new Recording(name, duration, frames);
        }
    }

    private static void writeFrame(DataOutputStream out, TickSnapshot frame) throws IOException {
        out.writeLong(frame.tick());
        out.writeInt(frame.entities().size());
        for (EntitySnapshot entity : frame.entities()) writeEntity(out, entity);
        out.writeInt(frame.actions().size());
        for (Action action : frame.actions()) writeString(out, action.toString());
    }

    private static TickSnapshot readFrame(DataInputStream in) throws IOException {
        long tick = in.readLong();
        int entities = checkedCount(in.readInt());
        List<EntitySnapshot> snapshots = new ArrayList<>(entities);
        for (int i = 0; i < entities; i++) snapshots.add(readEntity(in));
        int actions = checkedCount(in.readInt());
        List<Action> decodedActions = new ArrayList<>(actions);
        for (int i = 0; i < actions; i++) readString(in); // Action is currently marker/interface data; preserve frame compatibility.
        return new TickSnapshot(tick, snapshots, decodedActions);
    }

    private static void writeEntity(DataOutputStream out, EntitySnapshot e) throws IOException {
        out.writeInt(e.entityId());
        out.writeLong(e.uuid().getMostSignificantBits());
        out.writeLong(e.uuid().getLeastSignificantBits());
        writeString(out, e.type().name());
        out.writeDouble(e.x()); out.writeDouble(e.y()); out.writeDouble(e.z());
        out.writeFloat(e.yaw()); out.writeFloat(e.pitch());
        out.writeDouble(e.velocityX()); out.writeDouble(e.velocityY()); out.writeDouble(e.velocityZ());
        out.writeInt(e.flags());
        out.writeBoolean(e.exists());
        out.writeBoolean(e.equipment() != null);
        if (e.equipment() != null) writeString(out, e.equipment().toString());
        out.writeBoolean(e.identity() != null);
        if (e.identity() != null) writeString(out, e.identity().toString());
    }

    private static EntitySnapshot readEntity(DataInputStream in) throws IOException {
        int id = in.readInt();
        UUID uuid = new UUID(in.readLong(), in.readLong());
        EntityType type = EntityType.valueOf(readString(in));
        double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
        float yaw = in.readFloat(), pitch = in.readFloat();
        double vx = in.readDouble(), vy = in.readDouble(), vz = in.readDouble();
        int flags = in.readInt();
        boolean exists = in.readBoolean();
        if (in.readBoolean()) readString(in);
        if (in.readBoolean()) readString(in);
        return new EntitySnapshot(id, uuid, type, x, y, z, yaw, pitch, vx, vy, vz, flags, null, null, exists);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length); out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = checkedCount(in.readInt());
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new EOFException();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int checkedCount(int value) throws IOException {
        if (value < 0 || value > 10_000_000) throw new IOException("invalid recording data count: " + value);
        return value;
    }
}
