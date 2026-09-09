package com.ultraop.aurareplay.actor;

import java.util.Objects;
import java.util.UUID;

public record ActorId(UUID value) {
    public ActorId {
        Objects.requireNonNull(value, "value");
    }

    public static ActorId random() {
        return new ActorId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
