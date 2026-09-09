package com.ultraop.aurareplay.recording.action;

public sealed interface Action
        permits ChatAction, MarkerAction, BlockAction {
    long tick();
}
