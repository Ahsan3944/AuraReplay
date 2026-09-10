package com.ultraop.aurareplay.actor;

/** Deterministic animation/action event that can be replayed at a timeline tick. */
public enum ActorAction {
    SWING,
    USE,
    HURT,
    DEATH,
    START_SPRINT,
    STOP_SPRINT,
    START_SNEAK,
    STOP_SNEAK,
    START_GLIDE,
    STOP_GLIDE
}
