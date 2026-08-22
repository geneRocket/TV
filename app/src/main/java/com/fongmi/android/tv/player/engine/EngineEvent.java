package com.fongmi.android.tv.player.engine;

public final class EngineEvent {

    private final long sessionId;
    private final EnginePhase phase;
    private final long position;
    private final long duration;
    private final Throwable error;

    private EngineEvent(long sessionId, EnginePhase phase, long position, long duration, Throwable error) {
        this.sessionId = sessionId;
        this.phase = phase;
        this.position = position;
        this.duration = duration;
        this.error = error;
    }

    public static EngineEvent state(long sessionId, EnginePhase phase, long position, long duration) {
        return new EngineEvent(sessionId, phase, Math.max(position, 0), duration, null);
    }

    public static EngineEvent error(long sessionId, Throwable error, long position, long duration) {
        return new EngineEvent(sessionId, EnginePhase.ERROR, Math.max(position, 0), duration, error);
    }

    public long getSessionId() { return sessionId; }

    public EnginePhase getPhase() { return phase; }

    public long getPosition() { return position; }

    public long getDuration() { return duration; }

    public Throwable getError() { return error; }
}
