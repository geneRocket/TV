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

    public VodPlaybackResolver(ExecutorService executor, Consumer<Runnable> dispatcher, PlaybackGateway gateway,
                               Consumer<Result> resultPrepared, Predicate<Result> useParse) {
        this.executor = executor;
        this.dispatcher = dispatcher;
        this.gateway = gateway;
        this.resultPrepared = resultPrepared;
        this.useParse = useParse;
    }

    @Override public ResolveHandle resolve(ResolveRequest request, Callback callback) {
        Task task = new Task(request.getRequestId());
        task.future = executor.submit(() -> {
            try {
                Result result = gateway.resolve(request.getSiteKey(), request.getFlag(), request.getEpisodeUrl());
                dispatcher.accept(() -> resolveMedia(task, result, callback));
            } catch (Throwable error) {
                dispatcher.accept(() -> fail(task, callback, error));
            }
        });
        return task;
    }

    private void resolveMedia(Task task, Result result, Callback callback) {
        if (task.cancelled) return;
        resultPrepared.accept(result);
        sourceResolver.resolve(result, useParse.test(result), false, new PlaybackSourceResolver.Callback() {
            @Override public void onResolved(ResolvedMedia media) {
                if (task.cancelled) return;
                Result direct = Result.empty();
                direct.setUrl(media.getUrl());
                direct.setHeader(Json.toObject(media.getHeaders()));
                direct.setFormat(media.getFormat());
                direct.setDrm(media.getDrm());
                direct.setSubs(media.getSubtitles());
                direct.setFlag(result.getFlag());
                direct.setDanmaku(result.getDanmaku());
                direct.setParse(0);
                callback.onResolved(new ResolveResult(task.requestId, direct));
            }

            @Override public void onFailure() { fail(task, callback, new IllegalStateException("playback parse failed")); }
        });
    }

    private void fail(Task task, Callback callback, Throwable error) {
        if (!task.cancelled) callback.onFailure(new ResolveFailure(task.requestId, ResolveFailure.Kind.EXTRACT, error));
    }

    @Override public void close() {
        sourceResolver.close();
        executor.shutdownNow();
    }

    private final class Task implements ResolveHandle {
        private final long requestId;
        private volatile boolean cancelled;
        private Future<?> future;
        private Task(long requestId) { this.requestId = requestId; }
        @Override public long getRequestId() { return requestId; }
        @Override public void cancel() {
            cancelled = true;
            if (future != null) future.cancel(true);
            sourceResolver.cancel();
        }
    }
}
