package com.fongmi.android.tv.api.config;

/** One publication point for the VOD/live pair, including inherited live configurations. */
final class ConfigRuntime {
    static final class Snapshot {
        final VodConfig vod;
        final LiveConfig live;
        Snapshot(VodConfig vod, LiveConfig live) { this.vod = vod; this.live = live; }
    }
    private static volatile Snapshot current = new Snapshot(new VodConfig(), new LiveConfig());
    private ConfigRuntime() {}
    static Snapshot snapshot() { return current; }
    static synchronized void publish(VodConfig vod, LiveConfig live) { current = new Snapshot(vod, live); }
    static synchronized void publishVod(VodConfig vod) { current = new Snapshot(vod, current.live); }
    static synchronized void publishLive(LiveConfig live) { current = new Snapshot(current.vod, live); }
}
