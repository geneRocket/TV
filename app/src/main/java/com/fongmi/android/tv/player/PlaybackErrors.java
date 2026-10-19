package com.fongmi.android.tv.player;

import com.fongmi.android.tv.event.ErrorEvent;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** One controller's error callback; queued deliveries cannot outlive its playback. */
final class PlaybackErrors implements AutoCloseable {
    private final Executor delivery;
    private Consumer<ErrorEvent> listener;
    private long generation;

    PlaybackErrors(Executor delivery, Consumer<ErrorEvent> listener) {
        this.delivery = Objects.requireNonNull(delivery);
        this.listener = Objects.requireNonNull(listener);
    }

    void report(ErrorEvent error) {
        long request;
        synchronized (this) {
            if (listener == null) return;
            request = generation;
        }
        delivery.execute(() -> deliver(request, error));
    }

    private void deliver(long request, ErrorEvent error) {
        Consumer<ErrorEvent> target;
        synchronized (this) {
            if (generation != request || listener == null) return;
            target = listener;
        }
        target.accept(error);
    }

    synchronized void invalidate() {
        generation++;
    }

    @Override
    public synchronized void close() {
        invalidate();
        listener = null;
    }
}
