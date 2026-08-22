package com.fongmi.android.tv.player.resolver;

import com.fongmi.android.tv.bean.Result;
import com.github.catvod.utils.Json;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Resolves spider output and optional parser output before a player session is prepared. */
public final class VodPlaybackResolver implements PlaybackResolver, AutoCloseable {

    private final Consumer<Runnable> dispatcher;
    private final Consumer<Result> resultPrepared;
    private final Predicate<Result> useParse;
    private final PlaybackGateway gateway;
    private final ExecutorService executor;
    private final PlaybackSourceResolver sourceResolver = new PlaybackSourceResolver();
    private volatile Task activeMediaTask;
    private volatile boolean closed;

    public VodPlaybackResolver(ExecutorService executor, Consumer<Runnable> dispatcher, PlaybackGateway gateway,
                               Consumer<Result> resultPrepared, Predicate<Result> useParse) {
        if (executor == null) throw new IllegalArgumentException("executor must not be null");
        if (dispatcher == null) throw new IllegalArgumentException("dispatcher must not be null");
        if (gateway == null) throw new IllegalArgumentException("gateway must not be null");
        if (resultPrepared == null) throw new IllegalArgumentException("resultPrepared must not be null");
        if (useParse == null) throw new IllegalArgumentException("useParse must not be null");
        this.executor = executor;
        this.dispatcher = dispatcher;
        this.gateway = gateway;
        this.resultPrepared = resultPrepared;
        this.useParse = useParse;
    }

    @Override public ResolveHandle resolve(ResolveRequest request, Callback callback) {
        if (closed) throw new IllegalStateException("resolver is closed");
        if (request == null) throw new IllegalArgumentException("request must not be null");
        if (callback == null) throw new IllegalArgumentException("callback must not be null");
        Task task = new Task(request.getRequestId());
        try {
            task.future = executor.submit(() -> {
                try {
                    Result result = gateway.resolve(request.getSiteKey(), request.getFlag(), request.getEpisodeUrl());
                    dispatcher.accept(() -> resolveMedia(task, result, callback));
                } catch (Throwable error) {
                    dispatchFailure(task, callback, error);
                }
            });
        } catch (Throwable error) {
            finish(task);
            fail(task, callback, error);
        }
        return task;
    }

    private void resolveMedia(Task task, Result result, Callback callback) {
        if (task.cancelled) return;
        try {
            if (result == null) throw new IllegalStateException("playback gateway returned null");
            resultPrepared.accept(result);
            activeMediaTask = task;
            sourceResolver.resolve(result, useParse.test(result), false, new PlaybackSourceResolver.Callback() {
                @Override public void onResolved(ResolvedMedia media) {
                    if (task.cancelled) return;
                    finish(task);
                    Result direct = Result.empty();
                    direct.setUrl(media.getUrl());
                    direct.setHeader(Json.toObject(media.getHeaders()));
                    direct.setFormat(media.getFormat());
                    direct.setDrm(media.getDrm());
                    direct.setSubs(media.getSubtitles());
                    direct.setFlag(result.getFlag());
                    direct.setDanmaku(result.getDanmaku());
                    direct.setParse(0);
                    notifyResolved(callback, new ResolveResult(task.requestId, direct));
                }

                @Override public void onFailure() {
                    finish(task);
                    fail(task, callback, new IllegalStateException("playback parse failed"));
                }
            });
        } catch (Throwable error) {
            finish(task);
            fail(task, callback, error);
        }
    }

    private void dispatchFailure(Task task, Callback callback, Throwable error) {
        try {
            dispatcher.accept(() -> fail(task, callback, error));
        } catch (Throwable dispatchError) {
            fail(task, callback, dispatchError);
        }
    }

    private void fail(Task task, Callback callback, Throwable error) {
        if (!closed && !task.cancelled) {
            try {
                callback.onFailure(new ResolveFailure(task.requestId, error));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void notifyResolved(Callback callback, ResolveResult result) {
        if (closed) return;
        try {
            callback.onResolved(result);
        } catch (RuntimeException ignored) {
        }
    }

    private synchronized void finish(Task task) {
        if (activeMediaTask == task) activeMediaTask = null;
    }

    private synchronized void cancel(Task task) {
        task.cancelled = true;
        if (task.future != null) task.future.cancel(true);
        if (activeMediaTask == task) {
            activeMediaTask = null;
            sourceResolver.cancel();
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        Task active = activeMediaTask;
        if (active != null) cancel(active);
        sourceResolver.close();
        executor.shutdownNow();
    }

    private final class Task implements ResolveHandle {
        private final long requestId;
        private volatile boolean cancelled;
        private volatile Future<?> future;
        private Task(long requestId) { this.requestId = requestId; }
        @Override public long getRequestId() { return requestId; }
        @Override public void cancel() { VodPlaybackResolver.this.cancel(this); }
    }
}
