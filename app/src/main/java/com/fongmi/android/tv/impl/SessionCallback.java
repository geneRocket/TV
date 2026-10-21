package com.fongmi.android.tv.impl;

import android.support.v4.media.session.MediaSessionCompat;

import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.player.PlaybackController;
import com.fongmi.android.tv.player.PlaybackInput;

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
        if (player.dispatchInput(PlaybackInput.action(PlaybackInput.Type.PLAY))) return;
        ActionEvent.send(ActionEvent.PLAY);
    }

    @Override
    public void onPause() {
        if (player.dispatchInput(PlaybackInput.action(PlaybackInput.Type.PAUSE))) return;
        ActionEvent.send(ActionEvent.PAUSE);
    }

    @Override
    public void onSkipToPrevious() {
        if (player.dispatchInput(PlaybackInput.action(PlaybackInput.Type.PREVIOUS))) return;
        ActionEvent.send(ActionEvent.PREV);
    }

    @Override
    public void onSkipToNext() {
        if (player.dispatchInput(PlaybackInput.action(PlaybackInput.Type.NEXT))) return;
        ActionEvent.send(ActionEvent.NEXT);
    }

    @Override
    public void onStop() {
        if (player.dispatchInput(PlaybackInput.action(PlaybackInput.Type.STOP))) return;
        ActionEvent.send(ActionEvent.STOP);
    }
}
