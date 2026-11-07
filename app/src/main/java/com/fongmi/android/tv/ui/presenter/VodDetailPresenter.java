package com.fongmi.android.tv.ui.presenter;

import com.fongmi.android.tv.utils.TextValues;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;

/** Coordinates VOD detail requests and empty/error decisions without UI dependencies. */
public final class VodDetailPresenter {

    public interface Port {
        String nextRequestToken();
        default String siteKey() { return null; }
        default String videoId() { return null; }
        void setPendingRequest(String token);
        default void cancelPendingRequest() {}
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
        void offerOtherSources();
        void notifyMessage(String message);
    }

    private final Port port;
    private String token = "", site, video;
    public String token() { return token; }
    public void expect(String token, String site, String video) {
        this.token = token; this.site = site; this.video = video;
    }
    public void invalidate() { token = ""; port.cancelPendingRequest(); }
    public boolean accepts(Result result) {
        return result != null && port.accepts(result)
                && (site == null || java.util.Objects.equals(site, result.getKey()))
                && (video == null || java.util.Objects.equals(video, result.getRequestId()))
                && (site == null || java.util.Objects.equals(token, result.getRequestToken()));
    }

    public VodDetailPresenter(Port port) {
        this.port = port;
    }

    public void request() {
        String token = port.nextRequestToken();
        expect(token, port.siteKey(), port.videoId());
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
        if (!accepts(result)) return;
        port.clearSourceTimeout();
        if (result.getList().isEmpty()) empty(result.hasMsg() || result.isRequestFailed());
        else {
            Vod vod = result.getList().get(0);
            port.render(vod);
        }
        if (!TextValues.isEmpty(result.getMsg()) && !port.isSourceSwitching()) port.notifyMessage(result.getMsg());
    }

    private void empty(boolean failed) {
        if (port.isSourceSwitching()) {
            port.resumeSourceSearch();
            port.continueSourceSwitch();
        } else if (failed || port.shouldFinishWhenEmpty()) {
            if (port.hasFallbackName()) port.offerOtherSources();
            else port.finishScreen();
        } else if (!port.hasFallbackName()) {
            port.showEmptyState();
        } else {
            port.showFallbackName();
            port.scheduleEmptyFallback();
            port.recoverSource();
        }
    }
}
