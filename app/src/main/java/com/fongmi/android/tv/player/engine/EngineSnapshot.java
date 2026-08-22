package com.fongmi.android.tv.player.engine;

public final class EngineSnapshot {

    public static final EngineSnapshot EMPTY = new EngineSnapshot(0, EnginePhase.IDLE, 0, -1, 0, false, 1.0f, 0, 0);

    private final long sessionId;
    private final EnginePhase phase;
    private final long position;
    private final long duration;
    private final long bufferedPosition;
    private final boolean playing;
    private final float speed;
    private final int videoWidth;
    private final int videoHeight;

    public EngineSnapshot(long sessionId, EnginePhase phase, long position, long duration, long bufferedPosition, boolean playing, float speed, int videoWidth, int videoHeight) {
        if (sessionId < 0) throw new IllegalArgumentException("sessionId must not be negative");
        if (phase == null) throw new IllegalArgumentException("phase must not be null");
        this.sessionId = sessionId;
        this.phase = phase;
        this.position = Math.max(position, 0);
        this.duration = duration;
        this.bufferedPosition = Math.max(bufferedPosition, 0);
        this.playing = playing;
        this.speed = speed;
        this.videoWidth = Math.max(videoWidth, 0);
        this.videoHeight = Math.max(videoHeight, 0);
    }

    public long getSessionId() { return sessionId; }

    public EnginePhase getPhase() { return phase; }

    public long getPosition() { return position; }

    public long getDuration() { return duration; }

    public long getBufferedPosition() { return bufferedPosition; }

    public boolean isPlaying() { return playing; }

    public float getSpeed() { return speed; }

    public int getVideoWidth() { return videoWidth; }

    public int getVideoHeight() { return videoHeight; }
}
