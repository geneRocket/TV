package com.fongmi.android.tv.player;

public final class PlaybackSession {

    private long id;
    private boolean reserved;

    public long begin() {
        reserved = true;
        return ++id;
    }

    public long ensure() {
        if (!reserved) begin();
        reserved = false;
        return id;
    }

    public long getId() {
        return id;
    }

    public boolean isCurrent(long eventSessionId) {
        return id == eventSessionId;
    }
}
