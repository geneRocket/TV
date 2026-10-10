package com.fongmi.android.tv.player.vod;
import org.junit.Test;
import static org.junit.Assert.*;

public class VodHistoryCommitGateTest {
    private final VodHistoryCommitGate gate = new VodHistoryCommitGate();
    private final VodEpisode first = new VodEpisode("same-name", "site", "flag", "episode", "url-1");
    private final VodEpisode other = new VodEpisode("same-name", "site", "flag", "episode", "url-2");

    @Test public void prepareTimePauseCannotCommitAnUnplayedEpisode() {
        gate.onState(state(VodPlaybackPhase.PREPARING, first));
        gate.onState(state(VodPlaybackPhase.PAUSED, first));
        assertFalse(gate.canCommit(state(VodPlaybackPhase.PAUSED, first)));
    }

    @Test public void successfulProgressSurvivesPauseButDoesNotLeakIntoAnotherRoute() {
        gate.onState(state(VodPlaybackPhase.READY, first));
        assertTrue(gate.canCommit(state(VodPlaybackPhase.PAUSED, first)));
        assertTrue(gate.canCommit(state(VodPlaybackPhase.BUFFERING, first)));
        assertFalse(gate.canCommit(state(VodPlaybackPhase.PAUSED, other)));
        assertFalse(gate.canCommit(state(VodPlaybackPhase.ERROR, first)));
    }

    @Test public void movieResetInvalidatesEvenAnIdenticalEpisodeRoute() {
        gate.onState(state(VodPlaybackPhase.READY, first));
        gate.reset();
        assertFalse(gate.canCommit(state(VodPlaybackPhase.PAUSED, first)));
    }

    private VodPlaybackState state(VodPlaybackPhase phase, VodEpisode episode) {
        return new VodPlaybackState(phase, episode, 0, 100_000, 200_000, null);
    }
}
