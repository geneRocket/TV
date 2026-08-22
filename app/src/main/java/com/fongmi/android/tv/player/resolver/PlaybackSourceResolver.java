package com.fongmi.android.tv.player.resolver;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.player.ParseJob;

import java.util.Map;

/** Resolves a spider result, including optional second-stage parsing, into one immutable media value. */
public final class PlaybackSourceResolver implements AutoCloseable {

    public interface Callback {
        void onResolved(ResolvedMedia media);
        void onFailure();
    }

    private ParseJob job;
    private Callback callback;
    private Result input;
    private boolean forceLive;
    private long generation;
    private boolean closed;

    public synchronized void resolve(Result result, boolean useParse, boolean forceLive, Callback callback) {
        if (closed) throw new IllegalStateException("resolver is closed");
        if (result == null) throw new IllegalArgumentException("result must not be null");
        if (callback == null) throw new IllegalArgumentException("callback must not be null");
        cancel();
        this.input = result;
        this.forceLive = forceLive;
        this.callback = callback;
        if (result.getParse(1) == 1 || result.getJx() == 1) {
            long requestGeneration = generation;
            job = ParseJob.create(new ParseCallback() {
                @Override public void onParseSuccess(Map<String, String> headers, String url, String from) {
                    PlaybackSourceResolver.this.onParseSuccess(requestGeneration, headers, url, from);
                }

                @Override public void onParseError() {
                    PlaybackSourceResolver.this.onParseError(requestGeneration);
                }
            }).start(result, useParse);
        } else {
            deliver(new ResolvedMedia(result.getHeaders(), result.getRealUrl(), result.getFormat(), result.getDrm(), result.getSubs(), forceLive, ""));
        }
    }

    private synchronized void onParseSuccess(long requestGeneration, Map<String, String> headers, String url, String from) {
        if (requestGeneration != generation) return;
        Result result = input;
        if (result == null) return;
        deliver(new ResolvedMedia(headers, url, result.getFormat(), result.getDrm(), result.getSubs(), forceLive, from));
    }

    private synchronized void onParseError(long requestGeneration) {
        if (requestGeneration != generation) return;
        Callback target = callback;
        clear(false);
        if (target != null) target.onFailure();
    }

    private synchronized void deliver(ResolvedMedia media) {
        Callback target = callback;
        clear(false);
        if (target != null) target.onResolved(media);
    }

    public synchronized void cancel() {
        clear(true);
    }

    private synchronized void clear(boolean stop) {
        generation++;
        ParseJob current = job;
        job = null;
        callback = null;
        input = null;
        if (stop && current != null) current.stop();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        cancel();
        closed = true;
    }
}
