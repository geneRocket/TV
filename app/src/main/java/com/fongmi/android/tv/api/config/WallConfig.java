package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ThreadPools;
import java.io.UncheckedIOException;

/** Compatibility facade over isolated wallpaper drafts and one publication boundary. */
public final class WallConfig {
    private Config config;
    private boolean sync;
    private static volatile WallConfig current = new WallConfig();
    private static final WallpaperSelection selection = new WallpaperSelection();

    public static WallConfig get() { return current; }
    public static String getUrl() { return get().getConfig().getUrl(); }
    public static String getDesc() { return get().getConfig().getDesc(); }

    public static void load(Config config, Callback callback) {
        Config captured = config == null || config.isEmpty() ? get().getConfig().copy() : config.copy();
        selection.choose(captured.getUrl(), captured.getUrl().equals(VodConfig.get().getWall()),
                choice -> ConfigRequests.wall(candidate -> candidate.select(captured).loadConfig(callback, choice)));
    }

    public static void release() {
        selection.choose("", false, choice -> ConfigRequests.wall(candidate -> ConfigLoadTask.commit(() -> {
            current = candidate; selection.finish(choice);
        })));
    }

    public static void reload() {
        Config captured = Config.wall().copy();
        selection.choose(captured.getUrl(), captured.getUrl().equals(VodConfig.get().getWall()),
                choice -> ConfigRequests.wall(candidate -> candidate.publishSelection(captured, choice, false)));
    }

    /** VOD inheritance changes the selected source but does not download a new image. */
    static void synchronize(Config config) {
        Config captured = config.copy();
        WallConfig active = get();
        selection.inherit(active.getConfig().getUrl(), active.sync, captured.getUrl(),
                choice -> ConfigRequests.wall(candidate -> candidate.publishSelection(captured, choice, true)));
    }

    private void publishSelection(Config selected, WallpaperSelection.Choice choice, boolean persist) {
        try {
            select(selected);
            ConfigLoadTask.commit(() -> {
                if (persist) config.update();
                current = this;
            });
        } finally {
            selection.finish(choice);
        }
    }

    private WallConfig select(Config selected) {
        config = (selected == null || selected.isEmpty() ? Config.wall() : selected).copy();
        sync = config.getUrl().equals(VodConfig.get().getWall());
        return this;
    }

    public Config getConfig() { return config == null ? Config.wall() : config; }
    public void load(Callback callback) { load(getConfig(), callback); }

    private void loadConfig(Callback callback, WallpaperSelection.Choice choice) {
        try (WallpaperFileStore.Prepared image = WallpaperImages.store().prepare(config.getUrl(), FileUtil.getWall(0))) {
            ConfigLoadTask.commit(() -> {
                config.commitUrlEdit(() -> {
                    try { image.commit(); }
                    catch (java.io.IOException error) { throw new UncheckedIOException(error); }
                });
                config.update();
                current = this;
                selection.finish(choice);
                refresh(0);
            });
            ConfigLoadTask.post(callback::success);
        } catch (Throwable error) {
            selection.finish(choice);
            ConfigLoadTask.check();
            ConfigLoadTask.post(() -> callback.error(Notify.getError(R.string.error_config_parse, error)));
            ThreadPools.log(error, "Wallpaper config load failed; retaining current selection and image.");
        }
    }

    public boolean needSync(String url) {
        Config selected = getConfig();
        return selection.needSync(selected.getUrl(), sync, url);
    }

    public static void refresh(int index) {
        Setting.putWall(index);
        RefreshEvent.wall();
    }
}
