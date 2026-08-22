package com.fongmi.android.tv.player.vod;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.PlaybackHeaders;
import com.fongmi.android.tv.player.session.PlaybackItem;
import com.fongmi.android.tv.utils.UrlUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Creates normalized VOD items at the boundary between resolution and playback. */
public final class DefaultPlaybackItemFactory implements PlaybackItemFactory {

    private final Supplier<Sub> selectedSubtitle;

    public DefaultPlaybackItemFactory(Supplier<Sub> selectedSubtitle) {
        if (selectedSubtitle == null) throw new IllegalArgumentException("selectedSubtitle must not be null");
        this.selectedSubtitle = selectedSubtitle;
    }

    @Override
    public PlaybackItem create(VodEpisode episode, Result result, long startPosition) {
        if (episode == null) throw new IllegalArgumentException("episode must not be null");
        if (result == null) throw new IllegalArgumentException("result must not be null");
        String rawUrl = result.getRealUrl();
        Map<String, String> headers = new LinkedHashMap<>(result.getHeaders());
        headers.putAll(UrlUtil.getTagHeaders(rawUrl));
        String url = UrlUtil.stripTag(UrlUtil.normalize(rawUrl, ""));
        return new PlaybackItem(episode.getIdentity(), url, PlaybackHeaders.applyUserAgent(headers), result.getFormat(),
                result.getDrm(), subtitles(result.getSubs()), startPosition, false);
    }

    private List<Sub> subtitles(List<Sub> values) {
        List<Sub> result = values == null ? new ArrayList<>() : new ArrayList<>(values);
        Sub selected = selectedSubtitle.get();
        if (selected == null || TextUtils.isEmpty(selected.getUrl())) return result;
        for (Sub item : result) {
            if (item != null && TextUtils.equals(item.getUrl(), selected.getUrl())) return result;
        }
        result.add(0, selected);
        return result;
    }
}
