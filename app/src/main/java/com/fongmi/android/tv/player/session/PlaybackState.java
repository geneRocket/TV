package com.fongmi.android.tv.player.session;

public final class PlaybackState {

    public static final PlaybackState IDLE = new PlaybackState(0, PlaybackPhase.IDLE, null, 0, -1, null);

    private final long sessionId;
    private final PlaybackPhase phase;
    private final PlaybackItem item;
    private final long position;
    private final long duration;
    private final Throwable error;

    public PlaybackState(long sessionId, PlaybackPhase phase, PlaybackItem item, long position, long duration, Throwable error) {
        this.sessionId = sessionId;
        this.phase = phase;
        this.item = item;
        this.position = Math.max(position, 0);
        this.duration = duration;
        this.error = error;
    }

    public long getSessionId() { return sessionId; }

    public PlaybackPhase getPhase() { return phase; }

    public PlaybackItem getItem() { return item; }

    public long getPosition() { return position; }

    public long getDuration() { return duration; }

    public Throwable getError() { return error; }
}
