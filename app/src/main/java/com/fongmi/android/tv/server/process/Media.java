package com.fongmi.android.tv.server.process;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import com.fongmi.android.tv.player.PlaybackController;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.Server;
import com.google.gson.JsonObject;

import java.util.Map;
import fi.iki.elonen.NanoHTTPD;

public class Media implements Process {

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String path) {
        return "/media".equals(path);
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String path, Map<String, String> files) {
        PlaybackController player = Server.get().getPlayer();
        if (player == null) return Nano.success("{}");
        MediaSessionCompat mediaSession = player.getSession();
        if (mediaSession == null) return Nano.success("{}");
        PlaybackStateCompat playbackState = mediaSession.getController().getPlaybackState();
        MediaMetadataCompat metadata = mediaSession.getController().getMetadata();
        JsonObject result = new JsonObject();
        result.addProperty("url", getUrl(player));
        result.addProperty("state", getState(playbackState));
        result.addProperty("speed", getSpeed(playbackState));
        result.addProperty("title", getMetadataString(metadata, MediaMetadataCompat.METADATA_KEY_TITLE));
        result.addProperty("artist", getMetadataString(metadata, MediaMetadataCompat.METADATA_KEY_ARTIST));
        result.addProperty("artwork", getMetadataString(metadata, MediaMetadataCompat.METADATA_KEY_ART_URI));
        result.addProperty("duration", getDuration(metadata));
        result.addProperty("position", getPosition(playbackState));
        return Nano.success(result.toString());
    }

    private String getUrl(PlaybackController player) {
        String url = player.getUrl();
        return TextUtils.isEmpty(url) ? "" : url;
    }

    private String getMetadataString(MediaMetadataCompat metadata, String key) {
        if (metadata == null) return "";
        String value = metadata.getString(key);
        return TextUtils.isEmpty(value) ? "" : value;
    }

    private long getDuration(MediaMetadataCompat metadata) {
        return metadata == null ? -1 : metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
    }

    private int getState(PlaybackStateCompat playbackState) {
        return playbackState == null ? -1 : playbackState.getState();
    }

    private long getPosition(PlaybackStateCompat playbackState) {
        return playbackState == null ? -1 : playbackState.getPosition();
    }

    private float getSpeed(PlaybackStateCompat playbackState) {
        return playbackState == null ? -1 : playbackState.getPlaybackSpeed();
    }
}
