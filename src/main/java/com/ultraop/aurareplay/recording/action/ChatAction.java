package com.ultraop.aurareplay.recording.action;

import java.util.UUID;

public record ChatAction(long tick, UUID player, String message) implements Action {
}
