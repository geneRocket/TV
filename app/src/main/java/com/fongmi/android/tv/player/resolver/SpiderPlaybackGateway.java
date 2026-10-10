package com.fongmi.android.tv.player.resolver;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.repository.SpiderRepository;

public final class SpiderPlaybackGateway implements PlaybackGateway {

    @Override
    public Result resolve(String siteKey, String flag, String episodeUrl) throws Exception {
        Result result = SpiderRepository.get().playerContent(siteKey, flag, episodeUrl);
        if (result.hasMsg()) throw new com.fongmi.android.tv.exception.ExtractException(result.getMsg());
        if (result.isRequestFailed()) throw new com.fongmi.android.tv.exception.ExtractException(
                com.fongmi.android.tv.utils.ResUtil.getString(com.fongmi.android.tv.R.string.vod_load_failed));
        return result;
    }
}
