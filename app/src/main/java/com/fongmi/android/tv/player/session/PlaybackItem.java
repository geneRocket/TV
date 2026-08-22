package com.fongmi.android.tv.player.session;

import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Sub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlaybackItem {

    private final String identity;
    private final String url;
    private final Map<String, String> headers;
    private final String format;
    private final Drm drm;
    private final List<Sub> subtitles;
    private final long startPosition;
    private final boolean forceLive;

    public PlaybackItem(String identity, String url, Map<String, String> headers, String format, Drm drm, List<Sub> subtitles, long startPosition, boolean forceLive) {
        if (identity == null || identity.isEmpty()) throw new IllegalArgumentException("identity must not be empty");
        if (url == null || url.trim().isEmpty()) throw new IllegalArgumentException("url must not be empty");
        this.identity = identity;
        this.url = url;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers == null ? Collections.emptyMap() : headers));
        this.format = format;
        this.drm = drm;
        this.subtitles = Collections.unmodifiableList(new ArrayList<>(subtitles == null ? Collections.emptyList() : subtitles));
        this.startPosition = Math.max(startPosition, 0);
        this.forceLive = forceLive;
    }

    public String getIdentity() { return identity; }

    public String getUrl() { return url; }

    public Map<String, String> getHeaders() { return headers; }

    public String getFormat() { return format; }

    public Drm getDrm() { return drm; }

    public List<Sub> getSubtitles() { return subtitles; }

    public long getStartPosition() { return startPosition; }

    public boolean isForceLive() { return forceLive; }

}
