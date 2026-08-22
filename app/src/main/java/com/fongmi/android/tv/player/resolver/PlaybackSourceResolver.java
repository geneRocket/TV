package com.fongmi.android.tv.player.resolver;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.player.ParseJob;

import java.util.Map;

/** Resolves a spider result, including optional second-stage parsing, into one immutable media value. */
public final class PlaybackSourceResolver implements ParseCallback, AutoCloseable {

    public interface Callback {
        void onResolved(ResolvedMedia media);
        void onFailure();
    }

    private ParseJob job;
    private Callback callback;
    private Result input;
    private boolean forceLive;

    public void resolve(Result result, boolean useParse, boolean forceLive, Callback callback) {
        cancel();
        this.input = result;
        this.forceLive = forceLive;
        this.callback = callback;
        if (result.getParse(1) == 1 || result.getJx() == 1) {
            job = ParseJob.create(this).start(result, useParse);
        } else {
            deliver(new ResolvedMedia(result.getHeaders(), result.getRealUrl(), result.getFormat(), result.getDrm(), result.getSubs(), forceLive, ""));
        }
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        Result result = input;
        if (result == null) return;
        deliver(new ResolvedMedia(headers, url, result.getFormat(), result.getDrm(), result.getSubs(), forceLive, from));
    }

    @Override
    public void onParseError() {
        Callback target = callback;
        clear(false);
        if (target != null) target.onFailure();
    }

    private void deliver(ResolvedMedia media) {
        Callback target = callback;
        clear(false);
        if (target != null) target.onResolved(media);
    }

    public void cancel() {
        clear(true);
    }

    private void clear(boolean stop) {
        ParseJob current = job;
        job = null;
        callback = null;
        input = null;
        if (stop && current != null) current.stop();
    }

    @Override
    public void close() {
        cancel();
    }
}
