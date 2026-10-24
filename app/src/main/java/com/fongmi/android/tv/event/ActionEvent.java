package com.fongmi.android.tv.event;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.player.PlaybackInput;
import com.fongmi.android.tv.player.PlaybackOwners;

import org.greenrobot.eventbus.EventBus;

public class ActionEvent {

    public static String STOP = BuildConfig.APPLICATION_ID.concat(".stop");
    public static String PREV = BuildConfig.APPLICATION_ID.concat(".prev");
    public static String NEXT = BuildConfig.APPLICATION_ID.concat(".next");
    public static String PLAY = BuildConfig.APPLICATION_ID.concat(".play");
    public static String PAUSE = BuildConfig.APPLICATION_ID.concat(".pause");
    public static String UPDATE = BuildConfig.APPLICATION_ID.concat(".update");

    private final String action;

    public static void send(String action) {
        if (UPDATE.equals(action)) EventBus.getDefault().post(new ActionEvent(action));
        else PlaybackOwners.dispatch(PlaybackOwners.lease(PlaybackOwners.current()), input(action));
    }

    public static PlaybackInput input(String action) {
        if (PLAY.equals(action)) return PlaybackInput.action(PlaybackInput.Type.PLAY);
        if (PAUSE.equals(action)) return PlaybackInput.action(PlaybackInput.Type.PAUSE);
        if (STOP.equals(action)) return PlaybackInput.action(PlaybackInput.Type.STOP);
        if (NEXT.equals(action)) return PlaybackInput.action(PlaybackInput.Type.NEXT);
        if (PREV.equals(action)) return PlaybackInput.action(PlaybackInput.Type.PREVIOUS);
        return null;
    }

    public static void update() {
        send(UPDATE);
    }

    public static void next() {
        send(NEXT);
    }

    public static void prev() {
        send(PREV);
    }

    public static void pause() {
        send(PAUSE);
    }

    public ActionEvent(String action) {
        this.action = action;
    }

    public String getAction() {
        return action;
    }

    public boolean isUpdate() {
        return UPDATE.equals(getAction());
    }
}
