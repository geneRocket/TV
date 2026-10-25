package com.fongmi.android.tv.repository;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.utils.ConfigUrlParser;
import com.fongmi.android.tv.utils.ThreadPools;
import com.github.catvod.utils.Trans;
import java.util.*;

/** Application composition only. Storage implementations know no application singletons. */
public final class PersistenceServices {
    private PersistenceServices() {}
    private static final class Stores {
        static final HistoryStorage HISTORY = new HistoryStorage(() -> AppDatabase.get().getHistoryDao(),
                key -> AppDatabase.get().getTrackDao().delete(key), RefreshEvent::history,
                ThreadPools.newSingle("history-write"), App::execute, System::currentTimeMillis, Trans::s2t);
        static final KeepStorage KEEP = new KeepStorage(() -> AppDatabase.get().getKeepDao(), RefreshEvent::keep, App::execute);
    }
    public static HistoryStorage history() { return Stores.HISTORY; }
    public static KeepStorage keep() { return Stores.KEEP; }
    public static int currentId() { return VodConfig.get().getConfig().getId(); }
    public static PersistenceScope captureScope() {
        VodConfig vod = VodConfig.get();
        int fallback = vod.getConfig().getId();
        List<String> urls = vod.getLoadUrls();
        if (urls.isEmpty()) urls = ConfigUrlParser.parse(Setting.getVodConfigUrls());
        if (urls.isEmpty()) return PersistenceScope.single(fallback);
        Map<String, Integer> byUrl = new HashMap<>();
        for (Config item : ConfigRepository.get().urls()) byUrl.put(item.getUrl(), item.getId());
        List<Integer> ids = new ArrayList<>();
        for (String url : urls) {
            Integer id = url == null ? null : byUrl.get(url.trim());
            if (id != null && id > 0) ids.add(id);
        }
        return new PersistenceScope(fallback, ids);
    }
    public static Map<Integer, Integer> importIds(List<Config> configs) {
        Map<Integer, Integer> ids = new LinkedHashMap<>();
        for (Config config : configs) ids.putIfAbsent(config.getId(), ConfigRepository.get().find(config).getId());
        return Collections.unmodifiableMap(ids);
    }
}
