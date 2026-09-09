package com.ultraop.aurareplay.recording.action;

public record MarkerAction(long tick, String name) implements Action {
}
