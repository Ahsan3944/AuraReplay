package com.ultraop.aurareplay.recording.action;

public sealed interface Action
        permits ChatAction, MarkerAction, BlockAction, ActorActionRecord {
    long tick();
}
