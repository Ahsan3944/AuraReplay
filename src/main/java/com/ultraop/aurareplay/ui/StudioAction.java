package com.ultraop.aurareplay.ui;

/** Actions exposed by the Studio UI. Kept separate from rendering so commands and future input bridges can reuse them. */
public enum StudioAction {
    BACK,
    ACTORS,
    TRANSFORM,
    MOTION,
    APPEARANCE,
    CAMERA,
    EFFECTS,
    WORLD,
    PLAYBACK,
    PROJECT,
    SETTINGS,
    ACTOR_VISIBILITY,
    RECORD_START,
    RECORD_STOP,
    CAMERA_STATUS
}
