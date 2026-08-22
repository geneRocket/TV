package com.fongmi.android.tv.event;

import org.greenrobot.eventbus.EventBus;

public class PlayerEvent {

    private final int state;
    private final long sessionId;

    public static void prepare(long sessionId) {
        EventBus.getDefault().post(new PlayerEvent(0, sessionId));
    }

    public static void state(int state, long sessionId) {
        EventBus.getDefault().post(new PlayerEvent(state, sessionId));
    }

    private PlayerEvent(int state, long sessionId) {
        this.state = state;
        this.sessionId = sessionId;
    }

    public int getState() {
        return state;
    }

    public long getSessionId() {
        return sessionId;
    }
}
