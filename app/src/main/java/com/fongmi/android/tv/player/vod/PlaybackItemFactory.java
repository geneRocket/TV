package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.session.PlaybackItem;

public interface PlaybackItemFactory {
    PlaybackItem create(VodEpisode episode, Result result, long startPosition) throws Exception;
}
