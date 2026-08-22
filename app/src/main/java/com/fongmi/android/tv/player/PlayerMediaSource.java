package com.fongmi.android.tv.player;

import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Sub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlayerMediaSource {

    private final Map<String, String> headers;
    private final List<Sub> subtitles;
    private final String url;
    private final String format;
    private final Drm drm;
    private final long startPosition;
    private final boolean forceLive;

    public PlayerMediaSource(Map<String, String> headers, String url, String format, Drm drm, List<Sub> subtitles, long startPosition, boolean forceLive) {
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers == null ? Collections.emptyMap() : headers));
        this.subtitles = Collections.unmodifiableList(new ArrayList<>(subtitles == null ? Collections.emptyList() : subtitles));
        this.url = url == null ? "" : url;
        this.format = format;
        this.drm = drm;
        this.startPosition = Math.max(startPosition, 0);
        this.forceLive = forceLive;
    }

    public Map<String, String> getHeaders() { return headers; }

    public List<Sub> getSubtitles() { return subtitles; }

    public String getUrl() { return url; }

    public String getFormat() { return format; }

    public Drm getDrm() { return drm; }

    public long getStartPosition() { return startPosition; }

    public boolean isForceLive() { return forceLive; }
}
