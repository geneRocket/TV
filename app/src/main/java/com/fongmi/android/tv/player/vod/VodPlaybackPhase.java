package com.fongmi.android.tv.player.vod;

public enum VodPlaybackPhase {
    IDLE,
    RESOLVING,
    PREPARING,
    BUFFERING,
    READY,
    PAUSED,
    ENDED,
    ERROR,
    STOPPED,
    RELEASED
}
