package com.fongmi.android.tv.player.engine;

public enum EnginePhase {
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
