package com.fongmi.android.tv.player;

/** Keeps preview and committed seek positions anchored to one interaction origin. */
public final class PlaybackSeekGesture {

    private long origin;
    private boolean active;

    public long preview(long currentPosition, long duration, long offset) {
        if (!active) {
            origin = Math.max(currentPosition, 0);
            active = true;
        }
        return PlaybackSeek.target(origin, duration, offset);
    }

    public long commit(long currentPosition, long duration, long offset) {
        long base = active ? origin : Math.max(currentPosition, 0);
        reset();
        return PlaybackSeek.target(base, duration, offset);
    }

    public void reset() {
        active = false;
        origin = 0;
    }
}
