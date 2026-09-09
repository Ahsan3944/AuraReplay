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
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Versioned binary codec using periodic keyframes and per-entity deltas. */
public final class RecordingBinaryCodec {
    private static final int MAGIC = 0x41555241;
    private static final int VERSION = 3;
    static final int KEYFRAME_INTERVAL_FOR_INDEX = 20;

    private static final int POSITION = 1;
    private static final int ROTATION = 1 << 1;
    private static final int VELOCITY = 1 << 2;
    private static final int FLAGS = 1 << 3;
    private static final int EQUIPMENT = 1 << 4;
    private static final int IDENTITY = 1 << 5;
    private static final int EXISTS = 1 << 6;

    public byte[] encode(Recording recording) throws IOException {
        Objects.requireNonNull(recording, "recording");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(buffer); DataOutputStream out = new DataOutputStream(gzip)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            writeString(out, recording.name());
            out.writeLong(recording.durationTicks());
            out.writeInt(recording.frames().size());

            Map<EntityKey, EntitySnapshot> previous = new HashMap<>();
            for (int index = 0; index < recording.frames().size(); index++) {
                TickSnapshot frame = recording.frames().get(index);
                boolean keyframe = index == 0 || index % KEYFRAME_INTERVAL_FOR_INDEX == 0;
                out.writeLong(frame.tick());
                out.writeBoolean(keyframe);
                out.writeInt(frame.entities().size());

                Map<EntityKey, EntitySnapshot> current = new HashMap<>();
                for (EntitySnapshot entity : frame.entities()) {
                    EntityKey key = EntityKey.of(entity);
                    current.put(key, entity);
                    writeEntityDelta(out, entity, keyframe ? null : previous.get(key), keyframe);
                }
                previous = current;

                out.writeInt(frame.actions().size());
                for (Action action : frame.actions()) writeAction(out, action);
            }
        }
        return buffer.toByteArray();
    }

    public Recording decode(byte[] bytes) throws IOException {
        Objects.requireNonNull(bytes, "bytes");
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))) {
            if (in.readInt() != MAGIC) throw new IOException("invalid AuraReplay recording header");
            int version = in.readInt();
            if (version != VERSION) throw new IOException("unsupported AuraReplay recording version: " + version);

            String name = readString(in);
            long duration = in.readLong();
            int frameCount = checkedCount(in.readInt());
            List<TickSnapshot> frames = new ArrayList<>(frameCount);
            Map<EntityKey, EntitySnapshot> previous = new HashMap<>();

            for (int index = 0; index < frameCount; index++) {
                long tick = in.readLong();
                boolean keyframe = in.readBoolean();
                int entityCount = checkedCount(in.readInt());
                Map<EntityKey, EntitySnapshot> current = new HashMap<>();
                List<EntitySnapshot> entities = new ArrayList<>(entityCount);

                for (int i = 0; i < entityCount; i++) {
                    EntitySnapshot entity = readEntityDelta(in, keyframe, previous);
                    EntityKey key = EntityKey.of(entity);
                    current.put(key, entity);
                    entities.add(entity);
                }

                int actionCount = checkedCount(in.readInt());
                List<Action> actions = new ArrayList<>(actionCount);
                for (int i = 0; i < actionCount; i++) actions.add(readAction(in));
                frames.add(new TickSnapshot(tick, entities, actions));
                previous = current;
            }

            if (in.read() != -1) throw new IOException("trailing data after AuraReplay recording");
            return new Recording(name, duration, frames);
        }
    }

    private static void writeEntityDelta(DataOutputStream out, EntitySnapshot current, EntitySnapshot previous, boolean keyframe) throws IOException {
        EntityKey key = EntityKey.of(current);
        writeUuid(out, key.uuid());
        out.writeInt(key.entityId());
        writeString(out, current.type().name());
        int mask = keyframe || previous == null ? POSITION | ROTATION | VELOCITY | FLAGS | EQUIPMENT | IDENTITY | EXISTS : changedMask(current, previous);
        out.writeByte(mask);
        if ((mask & POSITION) != 0) { out.writeDouble(current.x()); out.writeDouble(current.y()); out.writeDouble(current.z()); }
        if ((mask & ROTATION) != 0) { out.writeFloat(current.yaw()); out.writeFloat(current.pitch()); }
        if ((mask & VELOCITY) != 0) { out.writeDouble(current.velocityX()); out.writeDouble(current.velocityY()); out.writeDouble(current.velocityZ()); }
        if ((mask & FLAGS) != 0) out.writeInt(current.flags());
        if ((mask & EQUIPMENT) != 0) { out.writeBoolean(current.equipment() != null); if (current.equipment() != null) writeEquipment(out, current.equipment()); }
        if ((mask & IDENTITY) != 0) { out.writeBoolean(current.identity() != null); if (current.identity() != null) writeIdentity(out, current.identity()); }
        if ((mask & EXISTS) != 0) out.writeBoolean(current.exists());
    }

    private static EntitySnapshot readEntityDelta(DataInputStream in, boolean keyframe, Map<EntityKey, EntitySnapshot> previous) throws IOException {
        UUID uuid = readNullableUuid(in);
        int entityId = in.readInt();
        EntityType type = EntityType.valueOf(readString(in));
        EntityKey key = new EntityKey(uuid, entityId);
        EntitySnapshot base = previous == null ? null : previous.get(key);
        if (keyframe || base == null) base = null;
        int mask = in.readUnsignedByte();
        double x = base == null ? 0 : base.x(), y = base == null ? 0 : base.y(), z = base == null ? 0 : base.z();
        float yaw = base == null ? 0 : base.yaw(), pitch = base == null ? 0 : base.pitch();
        double vx = base == null ? 0 : base.velocityX(), vy = base == null ? 0 : base.velocityY(), vz = base == null ? 0 : base.velocityZ();
        int flags = base == null ? 0 : base.flags();
        EquipmentSnapshot equipment = base == null ? null : base.equipment();
        EntityIdentitySnapshot identity = base == null ? null : base.identity();
        boolean exists = base == null || base.exists();
        if ((mask & POSITION) != 0) { x = in.readDouble(); y = in.readDouble(); z = in.readDouble(); }
        if ((mask & ROTATION) != 0) { yaw = in.readFloat(); pitch = in.readFloat(); }
        if ((mask & VELOCITY) != 0) { vx = in.readDouble(); vy = in.readDouble(); vz = in.readDouble(); }
        if ((mask & FLAGS) != 0) flags = in.readInt();
        if ((mask & EQUIPMENT) != 0) equipment = in.readBoolean() ? readEquipment(in) : null;
        if ((mask & IDENTITY) != 0) identity = in.readBoolean() ? readIdentity(in) : null;
        if ((mask & EXISTS) != 0) exists = in.readBoolean();
        return new EntitySnapshot(entityId, uuid, type, x, y, z, yaw, pitch, vx, vy, vz, flags, equipment, identity, exists);
    }

    private static int changedMask(EntitySnapshot a, EntitySnapshot b) {
        int mask = 0;
        if (Double.compare(a.x(), b.x()) != 0 || Double.compare(a.y(), b.y()) != 0 || Double.compare(a.z(), b.z()) != 0) mask |= POSITION;
        if (Float.compare(a.yaw(), b.yaw()) != 0 || Float.compare(a.pitch(), b.pitch()) != 0) mask |= ROTATION;
        if (Double.compare(a.velocityX(), b.velocityX()) != 0 || Double.compare(a.velocityY(), b.velocityY()) != 0 || Double.compare(a.velocityZ(), b.velocityZ()) != 0) mask |= VELOCITY;
        if (a.flags() != b.flags()) mask |= FLAGS;
        if (!Objects.equals(a.equipment(), b.equipment())) mask |= EQUIPMENT;
        if (!Objects.equals(a.identity(), b.identity())) mask |= IDENTITY;
        if (a.exists() != b.exists()) mask |= EXISTS;
        return mask;
    }

    private static void writeAction(DataOutputStream out, Action action) throws IOException {
        if (action instanceof ChatAction value) { out.writeByte(1); out.writeLong(value.tick()); writeUuid(out, value.player()); writeString(out, value.message()); }
        else if (action instanceof MarkerAction value) { out.writeByte(2); out.writeLong(value.tick()); writeString(out, value.name()); }
        else if (action instanceof BlockAction value) { out.writeByte(3); out.writeLong(value.tick()); out.writeInt(value.x()); out.writeInt(value.y()); out.writeInt(value.z()); writeString(out, value.blockData()); }
        else throw new IOException("unsupported action type: " + action.getClass().getName());
    }
    private static Action readAction(DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 1 -> new ChatAction(in.readLong(), readUuid(in), readString(in));
            case 2 -> new MarkerAction(in.readLong(), readString(in));
            case 3 -> new BlockAction(in.readLong(), in.readInt(), in.readInt(), in.readInt(), readString(in));
            default -> throw new IOException("unsupported recording action type");
        };
    }
    private static void writeEquipment(DataOutputStream out, EquipmentSnapshot e) throws IOException { writeItem(out, e.mainHand()); writeItem(out, e.offHand()); writeItem(out, e.helmet()); writeItem(out, e.chestplate()); writeItem(out, e.leggings()); writeItem(out, e.boots()); }
    private static EquipmentSnapshot readEquipment(DataInputStream in) throws IOException { return new EquipmentSnapshot(readItem(in), readItem(in), readItem(in), readItem(in), readItem(in), readItem(in)); }
    private static void writeItem(DataOutputStream out, ItemStack item) throws IOException { out.writeBoolean(item != null); if (item != null) writeBytes(out, item.serializeAsBytes()); }
    private static ItemStack readItem(DataInputStream in) throws IOException { return in.readBoolean() ? ItemStack.deserializeBytes(readBytes(in)) : null; }
    private static void writeIdentity(DataOutputStream out, EntityIdentitySnapshot i) throws IOException { writeUuid(out, i.profileUuid()); writeString(out, i.profileName()); writeNullableString(out, i.skinTextureValue()); writeNullableString(out, i.skinTextureSignature()); }
    private static EntityIdentitySnapshot readIdentity(DataInputStream in) throws IOException { return new EntityIdentitySnapshot(readUuid(in), readString(in), readNullableString(in), readNullableString(in)); }
    private static void writeUuid(DataOutputStream out, UUID value) throws IOException { out.writeBoolean(value != null); if (value != null) { out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits()); } }
    private static UUID readNullableUuid(DataInputStream in) throws IOException { return in.readBoolean() ? new UUID(in.readLong(), in.readLong()) : null; }
    private static UUID readUuid(DataInputStream in) throws IOException { UUID value = readNullableUuid(in); if (value == null) throw new IOException("required UUID is missing"); return value; }
    private static void writeNullableString(DataOutputStream out, String value) throws IOException { out.writeBoolean(value != null); if (value != null) writeString(out, value); }
    private static String readNullableString(DataInputStream in) throws IOException { return in.readBoolean() ? readString(in) : null; }
    private static void writeBytes(DataOutputStream out, byte[] value) throws IOException { out.writeInt(value.length); out.write(value); }
    private static byte[] readBytes(DataInputStream in) throws IOException { int n = checkedCount(in.readInt()); byte[] value = in.readNBytes(n); if (value.length != n) throw new EOFException(); return value; }
    private static void writeString(DataOutputStream out, String value) throws IOException { byte[] bytes = value.getBytes(StandardCharsets.UTF_8); out.writeInt(bytes.length); out.write(bytes); }
    private static String readString(DataInputStream in) throws IOException { int n = checkedCount(in.readInt()); byte[] bytes = in.readNBytes(n); if (bytes.length != n) throw new EOFException(); return new String(bytes, StandardCharsets.UTF_8); }
    private static int checkedCount(int value) throws IOException { if (value < 0 || value > 10_000_000) throw new IOException("invalid recording data count: " + value); return value; }
    private record EntityKey(UUID uuid, int entityId) { static EntityKey of(EntitySnapshot entity) { return new EntityKey(entity.uuid(), entity.entityId()); } }
}
