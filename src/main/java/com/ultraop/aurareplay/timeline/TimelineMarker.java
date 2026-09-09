package com.ultraop.aurareplay.timeline;

import java.util.Objects;

public record TimelineMarker(String id, long tick, String label) {
    public TimelineMarker {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        if (id.isBlank()) throw new IllegalArgumentException("marker id cannot be blank");
        if (tick < 0) throw new IllegalArgumentException("marker tick must be >= 0");
    }
}
