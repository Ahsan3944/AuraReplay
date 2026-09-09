package com.ultraop.aurareplay.recording.action;

public record BlockAction(
        long tick,
        int x,
        int y,
        int z,
        String blockData
) implements Action {
}
