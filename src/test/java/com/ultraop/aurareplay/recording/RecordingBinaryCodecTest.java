package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.recording.action.BlockAction;
import com.ultraop.aurareplay.recording.action.ChatAction;
import com.ultraop.aurareplay.recording.action.MarkerAction;
import com.ultraop.aurareplay.recording.snapshot.EntityIdentitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.EntitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class RecordingBinaryCodecTest {
    @Test
    void roundTripPreservesFramesActionsAndIdentity() throws Exception {
        UUID entityUuid = UUID.randomUUID();
        UUID profileUuid = UUID.randomUUID();
        EntityIdentitySnapshot identity = new EntityIdentitySnapshot(profileUuid, "Steve", "texture-value", "texture-signature");
        EntitySnapshot entity = new EntitySnapshot(42, entityUuid, EntityType.PLAYER, 1.25, 64.0, -2.5, 90.0f, 15.0f, 0.1, 0.2, 0.3, 7, null, identity, true);
        TickSnapshot frame = new TickSnapshot(12, List.of(entity), List.of(
                new ChatAction(12, profileUuid, "hello"),
                new MarkerAction(12, "ATTACK"),
                new BlockAction(12, 1, 64, -2, "minecraft:stone")
        ));
        Recording original = new Recording("TestTake", 20, List.of(frame));

        Recording decoded = roundTrip(original);

        assertEquals(original.name(), decoded.name());
        assertEquals(original.durationTicks(), decoded.durationTicks());
        assertEquals(original.frames(), decoded.frames());
        assertEquals(entity, decoded.frames().getFirst().entities().getFirst());
        assertEquals(frame.actions(), decoded.frames().getFirst().actions());
    }

    @Test
    void roundTripReconstructsDeltaFramesAndPeriodicKeyframes() throws Exception {
        UUID entityUuid = UUID.randomUUID();
        UUID profileUuid = UUID.randomUUID();
        EntityIdentitySnapshot identity = new EntityIdentitySnapshot(profileUuid, "Alex", "texture", "signature");
        EntitySnapshot first = new EntitySnapshot(7, entityUuid, EntityType.PLAYER, 10, 64, 20, 90, 10, 0.1, 0.2, 0.3, 3, null, identity, true);

        List<TickSnapshot> frames = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            double x = 10 + i;
            float yaw = 90 + i;
            EntitySnapshot entity = new EntitySnapshot(7, entityUuid, EntityType.PLAYER, x, 64, 20, yaw, 10, 0.1, 0.2, 0.3, 3, null, identity, true);
            frames.add(new TickSnapshot(i, List.of(i == 0 ? first : entity), List.of(new MarkerAction(i, "F" + i))));
        }
        Recording original = new Recording("DeltaTake", 22, frames);

        Recording decoded = roundTrip(original);

        assertEquals(original.name(), decoded.name());
        assertEquals(original.durationTicks(), decoded.durationTicks());
        assertEquals(original.frames().size(), decoded.frames().size());
        for (int i = 0; i < original.frames().size(); i++) {
            assertEquals(original.frames().get(i), decoded.frames().get(i), "frame " + i);
        }
        assertEquals(21.0, decoded.frames().get(21).entities().getFirst().x());
        assertEquals(111.0f, decoded.frames().get(21).entities().getFirst().yaw());
        assertEquals("F20", ((MarkerAction) decoded.frames().get(20).actions().getFirst()).name());
    }

    @Test
    void roundTripHandlesEntityAppearanceAndDisappearance() throws Exception {
        UUID entityUuid = UUID.randomUUID();
        EntitySnapshot entity = new EntitySnapshot(99, entityUuid, EntityType.ZOMBIE, 1, 2, 3, 0, 0, 0, 0, 0, 0, null, null, true);
        Recording original = new Recording("Lifecycle", 3, List.of(
                new TickSnapshot(0, List.of(), List.of()),
                new TickSnapshot(1, List.of(entity), List.of()),
                new TickSnapshot(2, List.of(), List.of())
        ));

        Recording decoded = roundTrip(original);

        assertEquals(original.name(), decoded.name());
        assertEquals(original.durationTicks(), decoded.durationTicks());
        assertEquals(original.frames(), decoded.frames());
        assertTrue(decoded.frames().get(0).entities().isEmpty());
        assertEquals(entity, decoded.frames().get(1).entities().getFirst());
        assertTrue(decoded.frames().get(2).entities().isEmpty());
    }

    @Test
    void rejectsWrongHeader() throws Exception {
        byte[] bytes = gzip(out -> {
            out.writeInt(0xDEADBEEF);
            out.writeInt(3);
        });
        assertThrows(IOException.class, () -> new RecordingBinaryCodec().decode(bytes));
    }

    @Test
    void rejectsUnsupportedVersion() throws Exception {
        byte[] bytes = gzip(out -> {
            out.writeInt(0x41555241);
            out.writeInt(999);
        });
        assertThrows(IOException.class, () -> new RecordingBinaryCodec().decode(bytes));
    }

    @Test
    void rejectsTruncatedRecording() throws Exception {
        UUID entityUuid = UUID.randomUUID();
        EntitySnapshot entity = new EntitySnapshot(1, entityUuid, EntityType.PLAYER, 1, 2, 3, 0, 0, 0, 0, 0, 0, null, null, true);
        byte[] encoded = new RecordingBinaryCodec().encode(new Recording("Truncated", 1, List.of(new TickSnapshot(0, List.of(entity), List.of()))));
        byte[] truncated = new byte[encoded.length - 2];
        System.arraycopy(encoded, 0, truncated, 0, truncated.length);
        assertThrows(IOException.class, () -> new RecordingBinaryCodec().decode(truncated));
    }

    private static Recording roundTrip(Recording recording) throws IOException {
        RecordingBinaryCodec codec = new RecordingBinaryCodec();
        return codec.decode(codec.encode(recording));
    }

    private static byte[] gzip(IoWriter writer) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(buffer); DataOutputStream out = new DataOutputStream(gzip)) {
            writer.write(out);
        }
        return buffer.toByteArray();
    }

    @FunctionalInterface
    private interface IoWriter {
        void write(DataOutputStream out) throws IOException;
    }
}
