package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.session.PlaybackItem;

public final class DirectPlaybackItemFactory implements PlaybackItemFactory {

    @Override
    public PlaybackItem create(VodEpisode episode, Result result, long startPosition) {
        String url = result.getRealUrl();
        if (url == null || url.isEmpty()) throw new IllegalArgumentException("resolved playback URL is empty");
        return new PlaybackItem(episode.getIdentity(), url, result.getHeaders(), result.getFormat(), result.getDrm(), result.getSubs(), startPosition, false);
    }
}
