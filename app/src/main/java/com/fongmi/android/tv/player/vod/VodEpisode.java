package com.fongmi.android.tv.player.vod;

public final class VodEpisode {

    private final String identity;
    private final String siteKey;
    private final String flag;
    private final String name;
    private final String resolverUrl;

    public VodEpisode(String identity, String siteKey, String flag, String name, String resolverUrl) {
        if (identity == null || identity.isEmpty()) throw new IllegalArgumentException("identity must not be empty");
        this.identity = identity;
        this.siteKey = siteKey;
        this.flag = flag == null ? "" : flag;
        this.name = name == null ? "" : name;
        this.resolverUrl = resolverUrl;
    }

    public String getIdentity() { return identity; }

    public String getSiteKey() { return siteKey; }

    public String getFlag() { return flag; }

    public String getName() { return name; }

    public String getResolverUrl() { return resolverUrl; }
}
