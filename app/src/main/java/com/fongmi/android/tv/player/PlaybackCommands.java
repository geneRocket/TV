package com.fongmi.android.tv.player;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Main-thread commands for one controller; never reroutes a request to its successor. */
final class PlaybackCommands implements AutoCloseable {
    private final Executor delivery;
    private final BooleanSupplier current;
    private Consumer<PlaybackCommand> listener;
    private long generation;

    PlaybackCommands(Executor delivery, BooleanSupplier current, Consumer<PlaybackCommand> listener) {
        this.delivery = Objects.requireNonNull(delivery);
        this.current = Objects.requireNonNull(current);
        this.listener = Objects.requireNonNull(listener);
    }

    void send(PlaybackCommand command) {
        long request;
        synchronized (this) {
            if (listener == null || command == null) return;
            request = generation;
        }
        delivery.execute(() -> {
            Consumer<PlaybackCommand> target;
            synchronized (this) {
                if (listener == null || generation != request) return;
                target = listener;
            }
            if (current.getAsBoolean()) target.accept(command);
        });
    }

    synchronized void invalidate() { generation++; }

    @Override
    public synchronized void close() {
        invalidate();
        listener = null;
    }
}
