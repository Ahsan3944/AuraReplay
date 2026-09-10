package com.ultraop.aurareplay.actor;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActorVisualStateDiffTest {
    @Test
    void firstStateReportsAllRenderableChanges() {
        ActorVisualState state = new ActorVisualState(7, null, null, true);
        assertEquals(Set.of(
                ActorVisualStateDiff.Change.FLAGS,
                ActorVisualStateDiff.Change.EQUIPMENT,
                ActorVisualStateDiff.Change.IDENTITY,
                ActorVisualStateDiff.Change.EXISTENCE),
                ActorVisualStateDiff.between(null, state));
    }

    @Test
    void unchangedStateProducesEmptyDiff() {
        ActorVisualState state = new ActorVisualState(7, null, null, true);
        assertTrue(ActorVisualStateDiff.between(state, state).isEmpty());
    }

    @Test
    void onlyChangedFieldsAreReported() {
        ActorVisualState previous = new ActorVisualState(1, null, null, true);
        ActorVisualState current = new ActorVisualState(3, null, null, true);
        assertEquals(Set.of(ActorVisualStateDiff.Change.FLAGS),
                ActorVisualStateDiff.between(previous, current));
    }
}
