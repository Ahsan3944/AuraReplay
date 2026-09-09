package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RecordingBuffer {

    private final ConcurrentLinkedQueue<TickSnapshot> queue = new ConcurrentLinkedQueue<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "AuraReplay-RecordingWriter");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean running;

    public void start() {
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
            List<TickSnapshot> batch = new ArrayList<>(64);
            for (int i = 0; i < 64; i++) {
                TickSnapshot snapshot = queue.poll();
                if (snapshot == null) break;
                batch.add(snapshot);
            }
            if (batch.isEmpty()) {
                Thread.onSpinWait();
                continue;
            }
            // Phase 1 keeps snapshots in memory. Persistence/compression is added
            // behind this boundary so the recording hot path stays unchanged.
        }
    }

    public void shutdown() {
        running = false;
        writer.shutdown();
    }
}
