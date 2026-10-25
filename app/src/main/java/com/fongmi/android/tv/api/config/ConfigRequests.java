package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.bean.Config;
import java.util.*;
import java.util.function.Consumer;

/** Owns load generations independently of whichever runtime snapshot is currently published. */
final class ConfigRequests {
    private ConfigRequests() {}
    private static final ConfigLoadTask VOD = new ConfigLoadTask();
    private static final ConfigLoadTask LIVE = new ConfigLoadTask();
    static void vod(Consumer<VodConfig> load) { VOD.submit("vod-config", () -> load.accept(new VodConfig())); }
    static void live(Consumer<LiveConfig> load) { LIVE.submit("live-config", () -> load.accept(new LiveConfig())); }
    static List<Config> capture(List<Config> configs) {
        List<Config> copy = new ArrayList<>(); for (Config config : configs) copy.add(config.copy());
        return Collections.unmodifiableList(copy);
    }
}
