package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.bean.Config;
import java.util.*;
import java.util.function.Consumer;

/** Owns load generations independently of whichever runtime snapshot is currently published. */
final class ConfigRequests {
    private ConfigRequests() {}
    private static final ConfigLoadTask VOD = new ConfigLoadTask();
    private static final ConfigLoadTask LIVE = new ConfigLoadTask();
    private static final ConfigLoadTask WALL = new ConfigLoadTask();
    private static volatile boolean playlistRequested;
    static void vod(Consumer<VodConfig> load) { VOD.submit("vod-config", () -> load.accept(new VodConfig())); }
    static void live(Consumer<LiveConfig> load) { live(false, load); }
    static synchronized void live(boolean playlist, Consumer<LiveConfig> load) {
        playlistRequested = playlist;
        LIVE.submit("live-config", () -> load.accept(new LiveConfig()));
    }
    static boolean playlistRequested() { return playlistRequested; }
    static void wall(Consumer<WallConfig> load) { WALL.submit("wall-config", () -> load.accept(new WallConfig())); }
    static List<Config> capture(List<Config> configs) {
        List<Config> copy = new ArrayList<>(); for (Config config : configs) copy.add(config.copy());
        return Collections.unmodifiableList(copy);
    }
}
