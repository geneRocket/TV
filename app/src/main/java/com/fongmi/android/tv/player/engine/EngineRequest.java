package com.fongmi.android.tv.player.engine;

import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Sub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EngineRequest {

    private final long sessionId;
    private final Map<String, String> headers;
    private final List<Sub> subtitles;
    private final String url;
    private final String format;
    private final Drm drm;
    private final long startPosition;
    private final boolean forceLive;
    private final boolean playWhenReady;

    public EngineRequest(long sessionId, String url, Map<String, String> headers, String format, Drm drm, List<Sub> subtitles, long startPosition, boolean forceLive) {
        this(sessionId, url, headers, format, drm, subtitles, startPosition, forceLive, true);
    }

    public EngineRequest(long sessionId, String url, Map<String, String> headers, String format, Drm drm, List<Sub> subtitles, long startPosition, boolean forceLive, boolean playWhenReady) {
        if (sessionId <= 0) throw new IllegalArgumentException("sessionId must be positive");
        if (url == null || url.trim().isEmpty()) throw new IllegalArgumentException("url must not be empty");
        this.sessionId = sessionId;
        this.url = url;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers == null ? Collections.emptyMap() : headers));
        this.format = format;
        this.drm = drm;
        this.subtitles = Collections.unmodifiableList(new ArrayList<>(subtitles == null ? Collections.emptyList() : subtitles));
        this.startPosition = startPosition == androidx.media3.common.C.TIME_UNSET
                ? startPosition : Math.max(startPosition, 0);
        this.forceLive = forceLive;
        this.playWhenReady = playWhenReady;
    }

    public long getSessionId() { return sessionId; }

    public String getUrl() { return url; }

    public Map<String, String> getHeaders() { return headers; }

    public String getFormat() { return format; }

    public Drm getDrm() { return drm; }

    public List<Sub> getSubtitles() { return subtitles; }

    public long getStartPosition() { return startPosition; }

    public boolean isForceLive() { return forceLive; }

    public boolean isPlayWhenReady() { return playWhenReady; }
}
