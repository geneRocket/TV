package com.fongmi.android.tv.player;

import com.fongmi.android.tv.event.ErrorEvent;

public final class PlaybackRecovery {

    private static final int MAX_ERRORS = 20;
    private static final int MAX_PLAYER_SWITCHES = 2;

    public enum Action {
        RETRY,
        TOGGLE_DECODE,
        RELOAD_EXO,
        SWITCH_PLAYER,
        ADVANCE,
        STOP
    }

    private int errors;
    private int playerSwitches;

    public Action next(ErrorEvent error, PlaybackController players, boolean hasPlayableItem) {
        if (++errors > MAX_ERRORS) {
            reset();
            return Action.STOP;
        }
        if (!players.exceedRetry(error.getRetry())) {
            if (error.isDecode() && players.canToggleDecode()) return Action.TOGGLE_DECODE;
            if (error.isExo() && players.isExo()) return Action.RELOAD_EXO;
            return Action.RETRY;
        }
        if (hasPlayableItem
                && error.isUrl()
                && error.getRetry() > 0
                && playerSwitches < MAX_PLAYER_SWITCHES
                && players.getPlayer() != PlayerType.SYSTEM) {
            playerSwitches++;
            return Action.SWITCH_PLAYER;
        }
        playerSwitches = 0;
        return Action.ADVANCE;
    }

    public void reset() {
        errors = 0;
        playerSwitches = 0;
    }
}
