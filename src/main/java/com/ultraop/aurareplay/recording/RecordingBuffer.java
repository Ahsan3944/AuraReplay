package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Thread boundary between the synchronous capture path and asynchronous processing.
 * The current phase keeps an in-memory immutable frame index; persistence is added behind this API.
 */
public final class RecordingBuffer {

    private final ConcurrentLinkedQueue<TickSnapshot> queue = new ConcurrentLinkedQueue<>();
    private final List<TickSnapshot> completed = new ArrayList<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "AuraReplay-RecordingWriter");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean running;

    public synchronized void start() {
        if (running) return;
        running = true;
        writer.submit(this::drainLoop);
    }

    public void append(TickSnapshot snapshot) {
        if (running) {
            queue.offer(snapshot);
        }
    }

    private void drainLoop() {
        while (running || !queue.isEmpty()) {
            TickSnapshot snapshot = queue.poll();
            if (snapshot == null) {
                Thread.onSpinWait();
                continue;
            }
            synchronized (completed) {
                completed.add(snapshot);
            }
        }
    }

    public List<TickSnapshot> stopAndSnapshot() {
        running = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!queue.isEmpty() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        synchronized (completed) {
            return List.copyOf(completed);
        }
    }

    public List<TickSnapshot> snapshot() {
        synchronized (completed) {
            return List.copyOf(completed);
        }
    }

    public void clear() {
        synchronized (completed) {
            completed.clear();
        }
        queue.clear();
    }

    public void shutdown() {
        running = false;
        writer.shutdown();
        try {
            writer.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }
}
