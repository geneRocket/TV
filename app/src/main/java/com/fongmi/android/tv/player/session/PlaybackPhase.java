package com.fongmi.android.tv.player.session;

public enum PlaybackPhase {
    IDLE,
    PREPARING,
    BUFFERING,
    READY,
    PAUSED,
    ENDED,
    ERROR,
    STOPPED,
    RELEASED
}
