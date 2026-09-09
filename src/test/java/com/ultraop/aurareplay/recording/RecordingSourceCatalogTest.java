package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorManager;
import com.ultraop.aurareplay.actor.ActorTransform;
import com.ultraop.aurareplay.recording.snapshot.EntityIdentitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.EntitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RecordingSourceCatalogTest {
    @Test
    void catalogsDistinctEntityStreamsByUuid() {
        UUID player = UUID.randomUUID();
        UUID zombie = UUID.randomUUID();
        Recording recording = new Recording("scene", 2, List.of(
                new TickSnapshot(0, List.of(
                        entity(1, player, EntityType.PLAYER, "Steve"),
                        entity(2, zombie, EntityType.ZOMBIE, null)
                )),
                new TickSnapshot(1, List.of(
                        entity(1, player, EntityType.PLAYER, "Steve"),
                        entity(2, zombie, EntityType.ZOMBIE, null)
                ))
        ));

        List<RecordingSource> sources = new RecordingSourceCatalog().sources(recording);

        assertEquals(2, sources.size());
        assertEquals("uuid:" + player, sources.get(0).key());
        assertEquals("Steve", sources.get(0).displayName());
        assertEquals(EntityType.ZOMBIE, sources.get(1).type());
    }

    @Test
    void actorCreatedFromSourceKeepsSharedRecordingAndSourceIdentity() {
        UUID sourceUuid = UUID.randomUUID();
        Recording recording = new Recording("take", 1, List.of(
                new TickSnapshot(0, List.of(entity(7, sourceUuid, EntityType.PLAYER, "Steve")))
        ));
        RecordingSource source = new RecordingSource(sourceUuid, 7, EntityType.PLAYER, "Steve");

        ActorManager actors = new ActorManager();
        ActorDefinition actor = actors.createFromSource(recording, source, ActorTransform.origin(0, 64, 0, 0, 0));

        assertSame(recording, actor.recording());
        assertEquals(sourceUuid, actor.sourceEntityUuid());
        assertEquals(7, actor.sourceEntityId());
    }

    private static EntitySnapshot entity(int id, UUID uuid, EntityType type, String name) {
        EntityIdentitySnapshot identity = name == null ? null : new EntityIdentitySnapshot(uuid, name, null, null);
        return new EntitySnapshot(id, uuid, type, 0, 64, 0, 0, 0, 0, 0, 0, 0, null, identity, true);
    }
}
