package com.fongmi.android.tv.player.resolver;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public final class AsyncPlaybackResolver implements PlaybackResolver, AutoCloseable {

    private final ExecutorService executor;
    private final Consumer<Runnable> callbackDispatcher;
    private final PlaybackGateway gateway;

    public AsyncPlaybackResolver(ExecutorService executor, Consumer<Runnable> callbackDispatcher, PlaybackGateway gateway) {
        this.executor = executor;
        this.callbackDispatcher = callbackDispatcher;
        this.gateway = gateway;
    }

    @Override
    public ResolveHandle resolve(ResolveRequest request, Callback callback) {
        Task task = new Task(request.getRequestId());
        task.future = executor.submit(() -> {
            try {
                ResolveResult result = new ResolveResult(request.getRequestId(), gateway.resolve(request.getSiteKey(), request.getFlag(), request.getEpisodeUrl()));
                callbackDispatcher.accept(() -> {
                    if (!task.cancelled) callback.onResolved(result);
                });
            } catch (Throwable error) {
                callbackDispatcher.accept(() -> {
                    if (!task.cancelled) callback.onFailure(new ResolveFailure(request.getRequestId(), ResolveFailure.Kind.EXTRACT, error));
                });
            }
        });
        return task;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private static final class Task implements ResolveHandle {

        private final long requestId;
        private volatile boolean cancelled;
        private Future<?> future;

        private Task(long requestId) {
            this.requestId = requestId;
        }

        @Override public long getRequestId() { return requestId; }

        @Override
        public void cancel() {
            cancelled = true;
            if (future != null) future.cancel(true);
        }
    }
}
