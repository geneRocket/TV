package com.fongmi.android.tv.player;

import com.fongmi.android.tv.event.ErrorEvent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlaybackRecoveryTest {

    @Test
    public void decodeFallbackIsAttemptedOnlyOncePerPlayback() {
        PlaybackRecovery recovery = new PlaybackRecovery();
        ErrorEvent error = new ErrorEvent(ErrorEvent.Type.URL, 1, 4001);

        assertEquals(PlaybackRecovery.Action.TOGGLE_DECODE, recovery.next(error, true, PlayerType.EXO, false));
        assertEquals(PlaybackRecovery.Action.ADVANCE, recovery.next(error, true, PlayerType.EXO, false));
    }

    @Test
    public void resetStartsAFreshRecoverySequence() {
        PlaybackRecovery recovery = new PlaybackRecovery();
        ErrorEvent error = new ErrorEvent(ErrorEvent.Type.URL, 1, 4001);

        recovery.next(error, true, PlayerType.EXO, true);
        recovery.reset();

        assertEquals(PlaybackRecovery.Action.TOGGLE_DECODE, recovery.next(error, true, PlayerType.EXO, true));
    }

    @Test public void explicitPlayerStillRetriesButDoesNotSwitchEngines() {
        PlaybackRecovery recovery = new PlaybackRecovery();
        ErrorEvent error = new ErrorEvent(ErrorEvent.Type.URL, 1, 4001);
        assertEquals(PlaybackRecovery.Action.TOGGLE_DECODE, recovery.next(error, true, PlayerType.EXO, true, false));
        assertEquals(PlaybackRecovery.Action.ADVANCE, recovery.next(error, true, PlayerType.EXO, true, false));
    }

    @Test public void defaultPlayerCanStillSwitchEnginesAfterRetry() {
        PlaybackRecovery recovery = new PlaybackRecovery();
        ErrorEvent error = new ErrorEvent(ErrorEvent.Type.URL, 1, 4001);
        assertEquals(PlaybackRecovery.Action.TOGGLE_DECODE, recovery.next(error, true, PlayerType.EXO, true, true));
        assertEquals(PlaybackRecovery.Action.SWITCH_PLAYER, recovery.next(error, true, PlayerType.EXO, true, true));
    }
}
