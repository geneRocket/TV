package com.fongmi.android.tv.player.vod;

public final class VodEpisode {

    public static String identity(String siteKey, String flag, String name, String resolverUrl) {
        return value(siteKey) + '\u001f' + value(flag) + '\u001f' + value(name) + '\u001f' + value(resolverUrl);
    }

    public static String resumeIdentity(String siteKey, String flag, String name) {
        return value(siteKey) + '\u001f' + value(flag) + '\u001f' + value(name);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    private final String identity;
    private final String siteKey;
    private final String flag;
    private final String name;
    private final String resolverUrl;

    public VodEpisode(String identity, String siteKey, String flag, String name, String resolverUrl) {
        if (identity == null || identity.trim().isEmpty()) throw new IllegalArgumentException("identity must not be empty");
        if (siteKey == null || siteKey.trim().isEmpty()) throw new IllegalArgumentException("siteKey must not be empty");
        if (resolverUrl == null || resolverUrl.trim().isEmpty()) throw new IllegalArgumentException("resolverUrl must not be empty");
        this.identity = identity.trim();
        this.siteKey = siteKey.trim();
        this.flag = value(flag);
        this.name = value(name);
        this.resolverUrl = resolverUrl.trim();
    }

    public String getIdentity() { return identity; }

    public String getSiteKey() { return siteKey; }

    public String getFlag() { return flag; }

    public String getName() { return name; }

    public String getResolverUrl() { return resolverUrl; }
}
