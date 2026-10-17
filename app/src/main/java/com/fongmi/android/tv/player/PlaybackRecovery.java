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
    private int retryAttempts;
    private int playerSwitches;
    private boolean decodeToggled;

    public Action next(ErrorEvent error, PlaybackController players, boolean hasPlayableItem) {
        return next(error, players, hasPlayableItem, true);
    }

    public Action next(ErrorEvent error, PlaybackController players, boolean hasPlayableItem, boolean allowPlayerSwitch) {
        return next(error, players.isExo(), players.getPlayer(), hasPlayableItem, allowPlayerSwitch);
    }

    Action next(ErrorEvent error, boolean exo, int player, boolean hasPlayableItem) {
        return next(error, exo, player, hasPlayableItem, true);
    }

    Action next(ErrorEvent error, boolean exo, int player, boolean hasPlayableItem, boolean allowPlayerSwitch) {
        if (++errors > MAX_ERRORS) {
            reset();
            return Action.STOP;
        }
        if (++retryAttempts <= Math.max(error.getRetry(), 0)) {
            if (error.isDecode() && exo && !decodeToggled) {
                decodeToggled = true;
                return Action.TOGGLE_DECODE;
            }
            if (error.isExo() && exo) return Action.RELOAD_EXO;
            return Action.RETRY;
        }
        if (hasPlayableItem
                && allowPlayerSwitch
                && error.isUrl()
                && error.getRetry() > 0
                && playerSwitches < MAX_PLAYER_SWITCHES
                && player != PlayerType.SYSTEM) {
            playerSwitches++;
            retryAttempts = 0;
            decodeToggled = false;
            return Action.SWITCH_PLAYER;
        }
        retryAttempts = 0;
        decodeToggled = false;
        playerSwitches = 0;
        return Action.ADVANCE;
    }

    public void reset() {
        errors = 0;
        retryAttempts = 0;
        playerSwitches = 0;
        decodeToggled = false;
    }
}
