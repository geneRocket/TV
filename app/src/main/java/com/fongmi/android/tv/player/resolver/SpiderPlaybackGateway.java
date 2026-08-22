package com.fongmi.android.tv.player.resolver;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.repository.SpiderRepository;

public final class SpiderPlaybackGateway implements PlaybackGateway {

    @Override
    public Result resolve(String siteKey, String flag, String episodeUrl) throws Exception {
        return SpiderRepository.get().playerContent(siteKey, flag, episodeUrl);
    }
}
