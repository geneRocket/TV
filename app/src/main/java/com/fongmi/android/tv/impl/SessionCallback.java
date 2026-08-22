package com.fongmi.android.tv.impl;

import android.support.v4.media.session.MediaSessionCompat;

import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.player.PlaybackController;

public class SessionCallback extends MediaSessionCompat.Callback {

    private final PlaybackController player;

    public static SessionCallback create(PlaybackController player) {
        return new SessionCallback(player);
    }

    private SessionCallback(PlaybackController player) {
        this.player = player;
    }

    @Override
    public void onSeekTo(long pos) {
        player.seekTo(pos);
    }

    @Override
    public void onPlay() {
        ActionEvent.send(ActionEvent.PLAY);
    }

    @Override
    public void onPause() {
        ActionEvent.send(ActionEvent.PAUSE);
    }

    @Override
    public void onSkipToPrevious() {
        ActionEvent.send(ActionEvent.PREV);
    }

    @Override
    public void onSkipToNext() {
        ActionEvent.send(ActionEvent.NEXT);
    }

    @Override
    public void onStop() {
        ActionEvent.send(ActionEvent.STOP);
    }
}
