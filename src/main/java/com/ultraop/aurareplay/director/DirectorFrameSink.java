package com.ultraop.aurareplay.director;

/** Receives deterministic director frames for a client-side renderer or encoder bridge. */
@FunctionalInterface
public interface DirectorFrameSink {
    void accept(DirectorFrame frame);
}
