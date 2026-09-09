package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.recording.action.Action;
import com.ultraop.aurareplay.recording.action.BlockAction;
import com.ultraop.aurareplay.recording.action.ChatAction;
import com.ultraop.aurareplay.recording.action.MarkerAction;
import com.ultraop.aurareplay.recording.snapshot.EntityIdentitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.EntitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.EquipmentSnapshot;
import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Versioned compressed binary codec. Format v2 preserves all currently modelled recording data. */
public final class RecordingBinaryCodec {
    private static final int MAGIC = 0x41555241;
    private static final int VERSION = 2;

    public byte[] encode(Recording recording) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(buffer); DataOutputStream out = new DataOutputStream(gzip)) {
            out.writeInt(MAGIC); out.writeInt(VERSION);
            writeString(out, recording.name()); out.writeLong(recording.durationTicks());
            out.writeInt(recording.frames().size());
            for (TickSnapshot frame : recording.frames()) writeFrame(out, frame);
        }
        return buffer.toByteArray();
    }

    public Recording decode(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))) {
            if (in.readInt() != MAGIC) throw new IOException("invalid AuraReplay recording header");
            if (in.readInt() != VERSION) throw new IOException("unsupported AuraReplay recording version");
            String name = readString(in); long duration = in.readLong();
            int frameCount = checkedCount(in.readInt());
            List<TickSnapshot> frames = new ArrayList<>(frameCount);
            for (int i = 0; i < frameCount; i++) frames.add(readFrame(in));
            return new Recording(name, duration, frames);
        }
    }

    private static void writeFrame(DataOutputStream out, TickSnapshot frame) throws IOException {
        out.writeLong(frame.tick()); out.writeInt(frame.entities().size());
        for (EntitySnapshot entity : frame.entities()) writeEntity(out, entity);
        out.writeInt(frame.actions().size()); for (Action action : frame.actions()) writeAction(out, action);
    }

    private static TickSnapshot readFrame(DataInputStream in) throws IOException {
        long tick = in.readLong(); int entities = checkedCount(in.readInt());
        List<EntitySnapshot> snapshots = new ArrayList<>(entities);
        for (int i = 0; i < entities; i++) snapshots.add(readEntity(in));
        int actions = checkedCount(in.readInt()); List<Action> decoded = new ArrayList<>(actions);
        for (int i = 0; i < actions; i++) decoded.add(readAction(in));
        return new TickSnapshot(tick, snapshots, decoded);
    }

    private static void writeAction(DataOutputStream out, Action action) throws IOException {
        if (action instanceof ChatAction value) {
            out.writeByte(1); out.writeLong(value.tick()); writeUuid(out, value.player()); writeString(out, value.message());
        } else if (action instanceof MarkerAction value) {
            out.writeByte(2); out.writeLong(value.tick()); writeString(out, value.name());
        } else if (action instanceof BlockAction value) {
            out.writeByte(3); out.writeLong(value.tick()); out.writeInt(value.x()); out.writeInt(value.y()); out.writeInt(value.z()); writeString(out, value.blockData());
        } else throw new IOException("unsupported action type: " + action.getClass().getName());
    }

    private static Action readAction(DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 1 -> new ChatAction(in.readLong(), readUuid(in), readString(in));
            case 2 -> new MarkerAction(in.readLong(), readString(in));
            case 3 -> new BlockAction(in.readLong(), in.readInt(), in.readInt(), in.readInt(), readString(in));
            default -> throw new IOException("unsupported recording action type");
        };
    }

    private static void writeEntity(DataOutputStream out, EntitySnapshot e) throws IOException {
        out.writeInt(e.entityId()); writeUuid(out, e.uuid()); writeString(out, e.type().name());
        out.writeDouble(e.x()); out.writeDouble(e.y()); out.writeDouble(e.z());
        out.writeFloat(e.yaw()); out.writeFloat(e.pitch());
        out.writeDouble(e.velocityX()); out.writeDouble(e.velocityY()); out.writeDouble(e.velocityZ());
        out.writeInt(e.flags()); out.writeBoolean(e.exists());
        out.writeBoolean(e.equipment() != null); if (e.equipment() != null) writeEquipment(out, e.equipment());
        out.writeBoolean(e.identity() != null); if (e.identity() != null) writeIdentity(out, e.identity());
    }

    private static EntitySnapshot readEntity(DataInputStream in) throws IOException {
        int id = in.readInt(); UUID uuid = readUuid(in); EntityType type = EntityType.valueOf(readString(in));
        double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
        float yaw = in.readFloat(), pitch = in.readFloat();
        double vx = in.readDouble(), vy = in.readDouble(), vz = in.readDouble(); int flags = in.readInt();
        boolean exists = in.readBoolean(); EquipmentSnapshot equipment = in.readBoolean() ? readEquipment(in) : null;
        EntityIdentitySnapshot identity = in.readBoolean() ? readIdentity(in) : null;
        return new EntitySnapshot(id, uuid, type, x, y, z, yaw, pitch, vx, vy, vz, flags, equipment, identity, exists);
    }

    private static void writeEquipment(DataOutputStream out, EquipmentSnapshot e) throws IOException {
        writeItem(out, e.mainHand()); writeItem(out, e.offHand()); writeItem(out, e.helmet());
        writeItem(out, e.chestplate()); writeItem(out, e.leggings()); writeItem(out, e.boots());
    }
    private static EquipmentSnapshot readEquipment(DataInputStream in) throws IOException {
        return new EquipmentSnapshot(readItem(in), readItem(in), readItem(in), readItem(in), readItem(in), readItem(in));
    }
    private static void writeItem(DataOutputStream out, ItemStack item) throws IOException {
        out.writeBoolean(item != null); if (item != null) writeBytes(out, item.serializeAsBytes());
    }
    private static ItemStack readItem(DataInputStream in) throws IOException {
        return in.readBoolean() ? ItemStack.deserializeBytes(readBytes(in)) : null;
    }

    private static void writeIdentity(DataOutputStream out, EntityIdentitySnapshot i) throws IOException {
        writeUuid(out, i.profileUuid()); writeString(out, i.profileName()); writeNullableString(out, i.skinTextureValue()); writeNullableString(out, i.skinTextureSignature());
    }
    private static EntityIdentitySnapshot readIdentity(DataInputStream in) throws IOException {
        return new EntityIdentitySnapshot(readUuid(in), readString(in), readNullableString(in), readNullableString(in));
    }

    private static void writeUuid(DataOutputStream out, UUID value) throws IOException { out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits()); }
    private static UUID readUuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static void writeNullableString(DataOutputStream out, String value) throws IOException { out.writeBoolean(value != null); if (value != null) writeString(out, value); }
    private static String readNullableString(DataInputStream in) throws IOException { return in.readBoolean() ? readString(in) : null; }
    private static void writeBytes(DataOutputStream out, byte[] value) throws IOException { out.writeInt(value.length); out.write(value); }
    private static byte[] readBytes(DataInputStream in) throws IOException { int n = checkedCount(in.readInt()); byte[] value = in.readNBytes(n); if (value.length != n) throw new EOFException(); return value; }
    private static void writeString(DataOutputStream out, String value) throws IOException { byte[] bytes = value.getBytes(StandardCharsets.UTF_8); out.writeInt(bytes.length); out.write(bytes); }
    private static String readString(DataInputStream in) throws IOException { int n = checkedCount(in.readInt()); byte[] bytes = in.readNBytes(n); if (bytes.length != n) throw new EOFException(); return new String(bytes, StandardCharsets.UTF_8); }
    private static int checkedCount(int value) throws IOException { if (value < 0 || value > 10_000_000) throw new IOException("invalid recording data count: " + value); return value; }
}
