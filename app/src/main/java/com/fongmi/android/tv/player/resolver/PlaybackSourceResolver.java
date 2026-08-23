package com.fongmi.android.tv.player.resolver;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.player.ParseJob;
import com.fongmi.android.tv.utils.ThreadPools;

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

    public void resolve(Result result, boolean useParse, boolean forceLive, Callback callback) {
        if (result == null) throw new IllegalArgumentException("result must not be null");
        if (callback == null) throw new IllegalArgumentException("callback must not be null");
        Callback directCallback = null;
        ResolvedMedia directMedia = null;
        synchronized (this) {
            if (closed) throw new IllegalStateException("resolver is closed");
            clearLocked(true);
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
                directMedia = new ResolvedMedia(result.getHeaders(), result.getRealUrl(), result.getFormat(), result.getDrm(), result.getSubs(), forceLive, "");
                directCallback = detach();
            }
        }
        notifyResolved(directCallback, directMedia);
    }

    private void onParseSuccess(long requestGeneration, Map<String, String> headers, String url, String from) {
        Callback target;
        ResolvedMedia media;
        synchronized (this) {
            if (requestGeneration != generation || input == null) return;
            media = new ResolvedMedia(headers, url, input.getFormat(), input.getDrm(), input.getSubs(), forceLive, from);
            target = detach();
        }
        notifyResolved(target, media);
    }

    private void onParseError(long requestGeneration) {
        Callback target;
        synchronized (this) {
            if (requestGeneration != generation) return;
            target = detach();
        }
        notifyFailure(target);
    }

    private void notifyResolved(Callback target, ResolvedMedia media) {
        if (target == null) return;
        try {
            target.onResolved(media);
        } catch (RuntimeException error) {
            ThreadPools.log(error, "Playback source resolved callback failed.");
        }
    }

    private void notifyFailure(Callback target) {
        if (target == null) return;
        try {
            target.onFailure();
        } catch (RuntimeException error) {
            ThreadPools.log(error, "Playback source failure callback failed.");
        }
    }

    private Callback detach() {
        Callback target = callback;
        clearLocked(false);
        return target;
    }

    public synchronized void cancel() {
        clearLocked(true);
    }

    private void clearLocked(boolean stop) {
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
