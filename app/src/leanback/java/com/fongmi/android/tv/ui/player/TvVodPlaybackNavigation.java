package com.fongmi.android.tv.ui.player;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Vod;

/** Coordinates TV playback choices without knowing Activities, adapters or views. */
public final class TvVodPlaybackNavigation {

    public interface Port {
        int currentEpisodeIndex();
        void capturePosition();
        boolean selectFlag(Flag flag, boolean notify);
        boolean isSourceSwitching();
        long currentPosition();
        void rememberFlagSwitchPosition(long position);
        void playFlag(Flag flag, int preferredEpisode);
        void selectEpisode(Episode episode);
        boolean selectParse(Parse parse, boolean notify);
        void refreshPlayback();
        void stopSourceSearch();
        void openDetail(Vod vod, boolean notify);
        Flag nextPlayableFlag();
        Vod nextPlayableSite();
        void beginSourceSwitch();
        void markCurrentSourceBroken();
        void startSourceLoading();
        void markSourceSelected();
    }

    private final Port port;

    public TvVodPlaybackNavigation(Port port) {
        this.port = port;
    }

    public void switchFlag(Flag flag, boolean notify) {
        int episode = port.currentEpisodeIndex();
        port.capturePosition();
        if (!port.selectFlag(flag, notify)) return;
        if (!port.isSourceSwitching()) port.rememberFlagSwitchPosition(port.currentPosition());
        port.playFlag(flag, episode);
    }

    public void switchEpisode(Episode episode) {
        port.selectEpisode(episode);
        port.stopSourceSearch();
    }

    public void switchParse(Parse parse, boolean notify) {
        if (!port.selectParse(parse, notify)) return;
        if (!port.isSourceSwitching()) port.stopSourceSearch();
        port.refreshPlayback();
    }

    public void showDetail(Vod vod, boolean notify) {
        port.openDetail(vod, notify);
    }

    public boolean nextFlag() {
        Flag next = port.nextPlayableFlag();
        if (next == null) return false;
        switchFlag(next, true);
        return true;
    }

    public void nextSite() {
        Vod next = port.nextPlayableSite();
        if (next == null) return;
        port.beginSourceSwitch();
        port.markCurrentSourceBroken();
        port.startSourceLoading();
        port.markSourceSelected();
        port.openDetail(next, true);
    }
}
