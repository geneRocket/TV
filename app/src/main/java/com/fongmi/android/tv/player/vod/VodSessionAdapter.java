package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.player.PlaybackController;
import com.fongmi.android.tv.player.PlayerType;
import com.fongmi.android.tv.player.session.SwitchablePlayerSession;

/** Low-level engine replacement shared by both pages; never prepares media itself. */
public final class VodSessionAdapter {
    private final PlaybackController players;
    private final SwitchablePlayerSession session;
    private final Runnable initialize;
    private final Runnable changed;

    public VodSessionAdapter(PlaybackController players, SwitchablePlayerSession session, Runnable initialize, Runnable changed) {
        this.players = players;
        this.session = session;
        this.initialize = initialize;
        this.changed = changed;
    }

    public void attach() { session.replace(players.session()); }
    public void selectPlayer(int type) { players.setPlayer(type); attach(); changed.run(); }
    public void nextPlayer() { players.nextPlayer(); attach(); changed.run(); }
    public void decode(boolean persist) { players.toggleDecode(persist); initialize.run(); attach(); changed.run(); }
    public void ensureSubtitleSession() { if (players.isIjk()) selectPlayer(PlayerType.EXO); }
    public void ensureCompatible(Drm drm) { if (players.ensureCompatiblePlayer(drm)) { attach(); changed.run(); } }
}
