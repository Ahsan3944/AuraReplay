package com.ultraop.aurareplay.director;

/** Receives complete deterministic Director render frames. */
@FunctionalInterface
public interface DirectorRenderFrameSink {
    void accept(DirectorRenderFrame frame);
}
