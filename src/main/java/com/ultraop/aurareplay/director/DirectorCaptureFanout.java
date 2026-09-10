package com.ultraop.aurareplay.director;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Delivers one capture lifecycle to multiple sinks while preserving frame order. */
public final class DirectorCaptureFanout implements DirectorCaptureSink {
    private final List<DirectorCaptureSink> sinks;

    public DirectorCaptureFanout(DirectorCaptureSink... sinks) {
        Objects.requireNonNull(sinks, "sinks");
        this.sinks = Arrays.stream(sinks).filter(Objects::nonNull).toList();
        if (this.sinks.isEmpty()) throw new IllegalArgumentException("at least one sink is required");
    }

    @Override public void start(DirectorExportSpec spec) { for (DirectorCaptureSink sink : sinks) sink.start(spec); }
    @Override public void accept(DirectorFrame frame) { for (DirectorCaptureSink sink : sinks) sink.accept(frame); }
    @Override public void complete() { for (DirectorCaptureSink sink : sinks) sink.complete(); }
    @Override public void pause() { for (DirectorCaptureSink sink : sinks) { try { sink.pause(); } catch (Throwable ignored) { } } }
    @Override public void cancel() { for (DirectorCaptureSink sink : sinks) { try { sink.cancel(); } catch (Throwable ignored) { } } }
    @Override public void fail(Throwable error) { for (DirectorCaptureSink sink : sinks) { try { sink.fail(error); } catch (Throwable ignored) { } } }
}
