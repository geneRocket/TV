package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.PlaybackController;

/** Shared platform effects. Pages implement presentation and navigation, never engine replacement ordering. */
public abstract class VodPlayerPort implements VodPlaybackFlow.Port {
    private final PlaybackController players;
    private final VodSessionAdapter adapter;
    protected VodPlayerPort(PlaybackController players, VodSessionAdapter adapter) {
        this.players = players;
        this.adapter = adapter;
    }
    @Override public boolean isCurrent() { return players.isCurrent(); }
    @Override public float speed() { return players.getSpeed(); }
    @Override public boolean errorAlreadyReported(Throwable error) {
        return error != null && players.session().state().getError() == error;
    }
    @Override public boolean selectSubtitle(Sub subtitle) { return players.selectSubtitle(subtitle); }
    @Override public void attachSubtitleSession() { adapter.ensureSubtitleSession(); }
    @Override public void selectPlayer(int player) { adapter.selectPlayer(player); }
    @Override public void toggleDecode(boolean persist) { adapter.decode(persist); }
    @Override public void nextPlayer() { adapter.nextPlayer(); }
    @Override public void clearForNewEpisode() { players.clear(); }
}
