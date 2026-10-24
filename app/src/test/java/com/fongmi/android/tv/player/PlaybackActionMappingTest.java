package com.fongmi.android.tv.player;

import com.fongmi.android.tv.event.ActionEvent;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackActionMappingTest {
    @Test public void notificationDismissalOnlyStopsBackgroundPlaybackWhileExplicitStopStillWorks() {
        assertFalse(PlaybackSystemActionPolicy.accepts(true, true));
        assertTrue(PlaybackSystemActionPolicy.accepts(true, false));
        assertTrue(PlaybackSystemActionPolicy.accepts(false, true));
        assertTrue(PlaybackSystemActionPolicy.accepts(false, false));
    }
    @Test public void systemActionsMapToTheSameTypedInputsAsPageAndMediaSessionControls() {
        String[] actions = {ActionEvent.PLAY, ActionEvent.PAUSE, ActionEvent.STOP, ActionEvent.NEXT, ActionEvent.PREV};
        PlaybackInput.Type[] types = {PlaybackInput.Type.PLAY, PlaybackInput.Type.PAUSE, PlaybackInput.Type.STOP, PlaybackInput.Type.NEXT, PlaybackInput.Type.PREVIOUS};
        for (int i = 0; i < actions.length; i++) assertEquals(types[i], ActionEvent.input(actions[i]).getType());
    }
    @Test public void displayRefreshAndUnknownBroadcastsCannotBecomePlaybackCommands() {
        assertNull(ActionEvent.input(ActionEvent.UPDATE));
        assertNull(ActionEvent.input(null));
        assertNull(ActionEvent.input("other.app.stop"));
    }
}
