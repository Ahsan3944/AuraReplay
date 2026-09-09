package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.recording.action.BlockAction;
import com.ultraop.aurareplay.recording.action.ChatAction;
import com.ultraop.aurareplay.recording.action.MarkerAction;
import com.ultraop.aurareplay.recording.snapshot.EntityIdentitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.EntitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

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

        Recording decoded = new RecordingBinaryCodec().decode(new RecordingBinaryCodec().encode(original));

        assertEquals(original.name(), decoded.name());
        assertEquals(original.durationTicks(), decoded.durationTicks());
        assertEquals(1, decoded.frames().size());
        assertEquals(entity, decoded.frames().getFirst().entities().getFirst());
        assertEquals(frame.actions(), decoded.frames().getFirst().actions());
    }

    @Test
    void rejectsWrongHeader() throws Exception {
        byte[] encoded = new RecordingBinaryCodec().encode(new Recording("x", 0, List.of()));
        encoded[encoded.length - 1] ^= 0x01;
        assertThrows(Exception.class, () -> new RecordingBinaryCodec().decode(encoded));
    }
}
