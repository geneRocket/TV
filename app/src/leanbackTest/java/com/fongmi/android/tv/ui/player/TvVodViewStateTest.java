package com.fongmi.android.tv.ui.player;

import com.fongmi.android.tv.player.session.PlaybackPhase;
import com.fongmi.android.tv.player.vod.VodPlaybackPhase;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TvVodViewStateTest {

    @Test
    public void resolvingMapsToEnginePreparingState() {
        TvVodViewState state = new TvVodViewState(VodPlaybackPhase.RESOLVING, 0, "episode", 0, -1, true, false, null);

        assertEquals(PlaybackPhase.PREPARING, state.getPlaybackPhase());
    }

    @Test
    public void allOtherPhasesMapExplicitly() {
        for (VodPlaybackPhase phase : VodPlaybackPhase.values()) {
            TvVodViewState state = new TvVodViewState(phase, -1, "", 0, -1, false, false, null);
            if (phase == VodPlaybackPhase.RESOLVING) assertEquals(PlaybackPhase.PREPARING, state.getPlaybackPhase());
            else assertEquals(phase.name(), state.getPlaybackPhase().name());
        }
    }
}
