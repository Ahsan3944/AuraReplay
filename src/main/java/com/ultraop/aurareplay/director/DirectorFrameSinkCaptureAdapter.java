package com.ultraop.aurareplay.director;

import java.util.Objects;

/** Adapts the existing frame-only sink to the lifecycle-aware capture boundary. */
public final class DirectorFrameSinkCaptureAdapter implements DirectorCaptureSink {
    private final DirectorFrameSink delegate;

    public DirectorFrameSinkCaptureAdapter(DirectorFrameSink delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void start(DirectorExportSpec spec) { Objects.requireNonNull(spec, "spec"); }

    @Override
    public void accept(DirectorFrame frame) { delegate.accept(Objects.requireNonNull(frame, "frame")); }

    @Override
    public void complete() { }

    public DirectorFrameSink delegate() { return delegate; }
}
