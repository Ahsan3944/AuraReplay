package com.ultraop.aurareplay.director;

/** Lifecycle-aware boundary for client renderers and external encoders. */
public interface DirectorCaptureSink {
    void start(DirectorExportSpec spec);

    void accept(DirectorFrame frame);

    void complete();

    /** Pauses capture while preserving resumable output state. */
    default void pause() { }

    default void cancel() { }

    default void fail(Throwable error) { }
}
