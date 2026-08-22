package com.fongmi.android.tv.player.resolver;

import com.fongmi.android.tv.bean.Result;

public interface PlaybackGateway {
    Result resolve(String siteKey, String flag, String episodeUrl) throws Exception;
}
