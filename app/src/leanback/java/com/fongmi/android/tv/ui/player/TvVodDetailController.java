package com.fongmi.android.tv.ui.player;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;

/** Coordinates VOD detail requests and empty/error decisions without UI dependencies. */
public final class TvVodDetailController {

    public interface Port {
        String nextRequestToken();
        void setPendingRequest(String token);
        void scheduleSourceTimeout();
        void requestDetail(String token);
        boolean accepts(Result result);
        void clearSourceTimeout();
        void prepareFor(Vod vod);
        void render(Vod vod);
        boolean isSourceSwitching();
        void resumeSourceSearch();
        void continueSourceSwitch();
        boolean shouldFinishWhenEmpty();
        boolean hasFallbackName();
        void finishScreen();
        void showEmptyState();
        void showFallbackName();
        void scheduleEmptyFallback();
        void recoverSource();
        void notifyMessage(String message);
    }

    private final Port port;

    public TvVodDetailController(Port port) {
        this.port = port;
    }

    public void request() {
        String token = port.nextRequestToken();
        port.setPendingRequest(token);
        port.scheduleSourceTimeout();
        port.requestDetail(token);
    }

    public void missing() {
        empty(false);
    }

    public void open(Vod vod) {
        port.prepareFor(vod);
        request();
    }

    public void accept(Result result) {
        if (!port.accepts(result)) return;
        port.clearSourceTimeout();
        if (result.getList().isEmpty()) empty(result.hasMsg());
        else {
            Vod vod = result.getList().get(0);
            port.render(vod);
        }
        if (!TextUtils.isEmpty(result.getMsg()) && !port.isSourceSwitching()) port.notifyMessage(result.getMsg());
    }

    private void empty(boolean failed) {
        if (port.isSourceSwitching()) {
            port.resumeSourceSearch();
            port.continueSourceSwitch();
        } else if (failed || port.shouldFinishWhenEmpty()) {
            port.finishScreen();
        } else if (!port.hasFallbackName()) {
            port.showEmptyState();
        } else {
            port.showFallbackName();
            port.scheduleEmptyFallback();
            port.recoverSource();
        }
    }
}
