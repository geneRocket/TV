package com.fongmi.android.tv.impl;

import android.support.v4.media.session.MediaSessionCompat;

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
        player.dispatchInput(PlaybackInput.seek(pos));
    }

    @Override
    public void onPlay() {
        player.dispatchInput(PlaybackInput.action(PlaybackInput.Type.PLAY));
    }

    @Override
    public void onPause() {
        player.dispatchInput(PlaybackInput.action(PlaybackInput.Type.PAUSE));
    }

    @Override
    public void onSkipToPrevious() {
        player.dispatchInput(PlaybackInput.action(PlaybackInput.Type.PREVIOUS));
    }

    @Override
    public void onSkipToNext() {
        player.dispatchInput(PlaybackInput.action(PlaybackInput.Type.NEXT));
    }

    @Override
    public void onStop() {
        player.dispatchInput(PlaybackInput.action(PlaybackInput.Type.STOP));
    }
}
