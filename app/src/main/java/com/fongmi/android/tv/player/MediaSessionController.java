package com.fongmi.android.tv.player;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.impl.SessionCallback;

/** Owns the Android media-session lifecycle for a playback coordinator. */
final class MediaSessionController {

    private MediaSessionCompat session;

    MediaSessionController(Activity activity, Players players) {
        session = new MediaSessionCompat(activity, "TV");
        session.setCallback(SessionCallback.create(players));
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setSessionActivity(PendingIntent.getActivity(App.get(), 0, new Intent(App.get(), activity.getClass()), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        MediaControllerCompat.setMediaController(activity, session.getController());
    }

    MediaSessionCompat getSession() {
        return session;
    }

    void setActive(boolean active) {
        if (session != null) session.setActive(active);
    }

    void setMetadata(MediaMetadataCompat metadata) {
        if (session != null) session.setMetadata(metadata);
    }

    void setPlaybackState(int state, long position, float speed) {
        if (session == null) return;
        long actions = PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        session.setPlaybackState(new PlaybackStateCompat.Builder().setActions(actions).setState(state, position, speed).build());
    }

    void release() {
        if (session == null) return;
        session.release();
        session = null;
    }
}
