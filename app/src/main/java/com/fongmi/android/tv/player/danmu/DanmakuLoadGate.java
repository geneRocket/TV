package com.fongmi.android.tv.player.danmu;

/** Main-thread ownership of one load per playback attempt/source, including async preparation. */
public final class DanmakuLoadGate {
    private long revision, playbackRequest;
    private String source = "";
    private boolean retained;

    /** Zero means that the current load already owns this source. */
    public long begin(long playbackRequest, String source) {
        if (source == null || source.isEmpty()) { invalidate(); return 0; }
        if (retained && this.playbackRequest == playbackRequest && this.source.equals(source)) return 0;
        this.playbackRequest = playbackRequest;
        this.source = source;
        retained = true;
        return ++revision;
    }

    public boolean accepts(long request) { return retained && request != 0 && request == revision; }

    public void failed(long request) {
        if (accepts(request)) invalidate();
    }

    public void invalidate() {
        revision++;
        retained = false;
        source = "";
    }
}
