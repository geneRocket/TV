package com.fongmi.android.tv.player.resolver;

import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Sub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ResolvedMedia {

    private final Map<String, String> headers;
    private final String url;
    private final String format;
    private final Drm drm;
    private final List<Sub> subtitles;
    private final boolean forceLive;
    private final String source;

    public ResolvedMedia(Map<String, String> headers, String url, String format, Drm drm, List<Sub> subtitles, boolean forceLive, String source) {
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers == null ? Collections.emptyMap() : headers));
        this.url = url == null ? "" : url;
        this.format = format;
        this.drm = drm;
        this.subtitles = Collections.unmodifiableList(new ArrayList<>(subtitles == null ? Collections.emptyList() : subtitles));
        this.forceLive = forceLive;
        this.source = source == null ? "" : source;
    }

    public Map<String, String> getHeaders() { return headers; }
    public String getUrl() { return url; }
    public String getFormat() { return format; }
    public Drm getDrm() { return drm; }
    public List<Sub> getSubtitles() { return subtitles; }
    public boolean isForceLive() { return forceLive; }
    public String getSource() { return source; }
}
