package com.ultraop.aurareplay.recording;

public final class EntityFlags {
    public static final int SNEAKING = 1;
    public static final int SWIMMING = 1 << 1;
    public static final int GLIDING = 1 << 2;
    public static final int INVISIBLE = 1 << 3;
    public static final int GLOWING = 1 << 4;
    public static final int ON_FIRE = 1 << 5;
    public static final int SPRINTING = 1 << 6;

    private EntityFlags() {}
}
