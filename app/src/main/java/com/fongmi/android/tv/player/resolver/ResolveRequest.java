package com.fongmi.android.tv.player.resolver;

public final class ResolveRequest {

    private final long requestId;
    private final String siteKey;
    private final String flag;
    private final String episodeUrl;

    public ResolveRequest(long requestId, String siteKey, String flag, String episodeUrl) {
        if (requestId <= 0) throw new IllegalArgumentException("requestId must be positive");
        if (siteKey == null || siteKey.isEmpty()) throw new IllegalArgumentException("siteKey must not be empty");
        if (episodeUrl == null || episodeUrl.isEmpty()) throw new IllegalArgumentException("episodeUrl must not be empty");
        this.requestId = requestId;
        this.siteKey = siteKey;
        this.flag = flag == null ? "" : flag;
        this.episodeUrl = episodeUrl;
    }

    public long getRequestId() { return requestId; }

    public String getSiteKey() { return siteKey; }

    public String getFlag() { return flag; }

    public String getEpisodeUrl() { return episodeUrl; }
}
