package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.bean.History;
import org.junit.Test;
import static org.junit.Assert.*;

public class VodHistoryProductTest {
    @Test public void onlySuccessfullyPlayedRoutesMayOverwriteHistoryIncludingBufferingOnExit() {
        VodEpisode original = new VodEpisode("one", "site", "flag", "one", "url-1");
        VodEpisode replacement = new VodEpisode("one", "site", "other", "one", "url-2");
        for (boolean previouslyPlayed : new boolean[]{false, true}) {
            for (VodEpisode episode : new VodEpisode[]{original, replacement}) {
                for (VodPlaybackPhase phase : VodPlaybackPhase.values()) {
                    VodHistoryCommitGate gate = new VodHistoryCommitGate();
                    VodHistoryController controller = new VodHistoryController();
                    if (previouslyPlayed) gate.onState(new VodPlaybackState(VodPlaybackPhase.READY, original, 0, 120_000, 900_000, null));
                    VodPlaybackState state = new VodPlaybackState(phase, episode, 0, 480_000, 900_000, null);
                    // The new state has not reached READY yet: merely selecting a
                    // new route/episode must not erase the last playable progress.
                    boolean canCommit = gate.canCommit(state) && controller.shouldCommit(phase);
                    boolean expected = previouslyPlayed && episode == original
                            && (phase == VodPlaybackPhase.READY || phase == VodPlaybackPhase.PAUSED
                            || phase == VodPlaybackPhase.BUFFERING || phase == VodPlaybackPhase.ENDED
                            || phase == VodPlaybackPhase.SUSPENDED);
                    assertEquals(previouslyPlayed + "/" + episode.getResolverUrl() + "/" + phase, expected, canCommit);
                    History history = new History(); history.setPosition(120_000); history.setDuration(900_000);
                    controller.updateProgress(history, canCommit, state.getPosition(), state.getDuration());
                    assertEquals(expected ? 480_000 : 120_000, history.getPosition());
                }
            }
        }
    }

    @Test public void seekingToStartIsExplicitButUnknownDurationCannotEraseKnownDuration() {
        VodHistoryController controller = new VodHistoryController();
        History history = new History(); history.setPosition(480_000); history.setDuration(900_000);
        assertTrue(controller.updateProgress(history, true, 0, -1));
        assertEquals(0, history.getPosition());
        assertEquals(900_000, history.getDuration());
        assertFalse(controller.updateProgress(history, true, -1, -1));
        assertEquals(0, history.getPosition());
    }
}
