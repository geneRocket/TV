package com.fongmi.android.tv.event;

import org.greenrobot.eventbus.EventBus;

public class RefreshEvent {

    private final Type type;

    public static void config() {
        EventBus.getDefault().post(new RefreshEvent(Type.CONFIG));
    }

    public static void image() {
        EventBus.getDefault().post(new RefreshEvent(Type.IMAGE));
    }

    public static void video() {
        EventBus.getDefault().post(new RefreshEvent(Type.VIDEO));
    }

    public static void history() {
        EventBus.getDefault().post(new RefreshEvent(Type.HISTORY));
    }

    public static void keep() {
        EventBus.getDefault().post(new RefreshEvent(Type.KEEP));
    }

    public static void size() {
        EventBus.getDefault().post(new RefreshEvent(Type.SIZE));
    }

    public static void wall() {
        EventBus.getDefault().post(new RefreshEvent(Type.WALL));
    }

    public static void live() {
        EventBus.getDefault().post(new RefreshEvent(Type.LIVE));
    }

    private RefreshEvent(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public enum Type {
        CONFIG, IMAGE, VIDEO, HISTORY, KEEP, SIZE, WALL, LIVE
    }
}
