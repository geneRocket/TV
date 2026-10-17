package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.PlaybackHeaders;
import com.fongmi.android.tv.player.session.PlaybackItem;
import com.fongmi.android.tv.utils.UrlUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/** Creates normalized VOD items at the boundary between resolution and playback. */
public final class DefaultPlaybackItemFactory implements PlaybackItemFactory {

    private final Supplier<Sub> selectedSubtitle;
    private final java.util.function.Predicate<String> blockedUrl;
    private final Function<String, String> urlNormalizer;
    private final Function<String, Map<String, String>> tagHeaders;
    private final Function<Map<String, String>, Map<String, String>> headerNormalizer;

    public DefaultPlaybackItemFactory(Supplier<Sub> selectedSubtitle) {
        this(selectedSubtitle, url -> UrlUtil.stripTag(UrlUtil.normalize(url, "")), UrlUtil::getTagHeaders,
                PlaybackHeaders::applyUserAgent, url -> com.fongmi.android.tv.Setting.isRemoveAd()
                        && com.fongmi.android.tv.utils.AdBlocker.isAdUrl(url));
    }

    DefaultPlaybackItemFactory(Supplier<Sub> selectedSubtitle, Function<String, String> urlNormalizer,
            Function<String, Map<String, String>> tagHeaders,
            Function<Map<String, String>, Map<String, String>> headerNormalizer) {
        this(selectedSubtitle, urlNormalizer, tagHeaders, headerNormalizer, url -> false);
    }

    DefaultPlaybackItemFactory(Supplier<Sub> selectedSubtitle, Function<String, String> urlNormalizer,
            Function<String, Map<String, String>> tagHeaders,
            Function<Map<String, String>, Map<String, String>> headerNormalizer,
            java.util.function.Predicate<String> blockedUrl) {
        if (selectedSubtitle == null) throw new IllegalArgumentException("selectedSubtitle must not be null");
        if (urlNormalizer == null) throw new IllegalArgumentException("urlNormalizer must not be null");
        if (tagHeaders == null) throw new IllegalArgumentException("tagHeaders must not be null");
        if (headerNormalizer == null) throw new IllegalArgumentException("headerNormalizer must not be null");
        this.blockedUrl = java.util.Objects.requireNonNull(blockedUrl);
        this.selectedSubtitle = selectedSubtitle;
        this.urlNormalizer = urlNormalizer;
        this.tagHeaders = tagHeaders;
        this.headerNormalizer = headerNormalizer;
    }

    @Override
    public PlaybackItem create(VodEpisode episode, Result result, long startPosition) {
        if (episode == null) throw new IllegalArgumentException("episode must not be null");
        if (result == null) throw new IllegalArgumentException("result must not be null");
        String rawUrl = result.getRealUrl();
        Map<String, String> headers = new LinkedHashMap<>(result.getHeaders());
        headers.putAll(tagHeaders.apply(rawUrl));
        String url = urlNormalizer.apply(rawUrl);
        if (blockedUrl.test(url)) throw new IllegalArgumentException("Blocked media URL");
        return new PlaybackItem(episode.getIdentity(), url, headerNormalizer.apply(headers), result.getFormat(),
                result.getDrm(), subtitles(result.getSubs()), startPosition, false);
    }

    private List<Sub> subtitles(List<Sub> values) {
        return Sub.withSelected(values, selectedSubtitle.get());
    }
}
