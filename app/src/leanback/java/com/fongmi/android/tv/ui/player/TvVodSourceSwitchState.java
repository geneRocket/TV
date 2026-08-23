package com.fongmi.android.tv.ui.player;

/** Small explicit state machine for automatic source replacement. */
public final class TvVodSourceSwitchState {

    public enum Phase { IDLE, SEARCHING, LOADING }

    private Phase phase = Phase.IDLE;
    private String episode = "";

    public Phase getPhase() { return phase; }
    public String getEpisode() { return episode; }
    public boolean isActive() { return phase != Phase.IDLE; }
    public boolean isLoading() { return phase == Phase.LOADING; }

    public void search(String episode) {
        phase = Phase.SEARCHING;
        this.episode = episode == null ? "" : episode;
    }

    public void resumeSearch() {
        if (isActive()) phase = Phase.SEARCHING;
    }

    public void startLoading() {
        if (isActive()) phase = Phase.LOADING;
    }

    public void clear() {
        phase = Phase.IDLE;
        episode = "";
    }
}
