package com.ultraop.aurareplay.recording.snapshot;

import java.util.List;

public record TickSnapshot(
        long tick,
        List<EntitySnapshot> entities
) {}
