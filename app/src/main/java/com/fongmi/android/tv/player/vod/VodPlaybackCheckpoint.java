package com.fongmi.android.tv.player.vod;

/** Immutable playback handoff payload; user pause is independent of temporary playback permission. */
public final class VodPlaybackCheckpoint {
    private final VodEpisode episode;
    private final long position, duration;
    private final float speed;
    private final boolean wantsPlayback;
    public VodPlaybackCheckpoint(VodEpisode episode, long position, long duration, float speed, boolean wantsPlayback) {
        this.episode = episode;
        this.position = Math.max(0, position);
        this.duration = duration;
        this.speed = Float.isFinite(speed) && speed > 0 ? speed : 1;
        this.wantsPlayback = wantsPlayback;
    }
    public VodEpisode getEpisode() { return episode; }
    public long getPosition() { return position; }
    public long getDuration() { return duration; }
    public float getSpeed() { return speed; }
    public boolean wantsPlayback() { return wantsPlayback; }
}
