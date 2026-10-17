package com.fongmi.android.tv.api.config;

import android.graphics.Bitmap;
import android.text.TextUtils;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.FutureTarget;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.repository.ConfigRepository;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.ThreadPools;
import com.github.catvod.net.HttpCallScope;
import com.github.catvod.utils.Asset;
import com.github.catvod.utils.Path;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class WallConfig {

    private Config config;
    private boolean sync;

    private static class Loader {
        static volatile WallConfig INSTANCE = new WallConfig();
    }

    public static WallConfig get() {
        return Loader.INSTANCE;
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public static void load(Config config, Callback callback) {
        get().submit(() -> get().clear().config(config).loadConfig(callback));
    }

    /** Clears shared state after already queued configuration work has finished. */
    public static void release() {
        get().submit(get()::clear);
    }

    public static void reload() {
        get().submit(() -> get().config(Config.wall()));
    }

    private void submit(Runnable task) {
        ThreadPools.configLoad().execute(() -> {
            synchronized (this) {
                task.run();
            }
        });
    }

    public WallConfig init() {
        return config(new Config());
    }

    public WallConfig config(Config config) {
        this.config = config;
        if (config.getUrl() == null) return this;
        this.sync = config.getUrl().equals(VodConfig.get().getWall());
        return this;
    }

    public WallConfig clear() {
        this.config = null;
        return this;
    }

    public Config getConfig() {
        return config == null ? Config.wall() : config;
    }

    public void load(Callback callback) {
        submit(() -> loadConfig(callback));
    }

    private void loadConfig(Callback callback) {
        try {
            if (config == null || config.isEmpty()) config(Config.wall());
            write(FileUtil.getWall(0));
            config.update();
            refresh(0);
            App.post(callback::success);
        } catch (Throwable e) {
            App.post(() -> callback.error(Notify.getError(R.string.error_config_parse, e)));
            config(ConfigRepository.get().find(VodConfig.get().getWall(), 2));
            ThreadPools.log(e, "Wallpaper config load failed.");
        }
    }

    File write(File file) throws Exception {
        HttpCallScope.throwIfCancelled();
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) throw new IOException("Cannot create wallpaper directory");
        File staged = File.createTempFile("wallpaper-", ".tmp", parent);
        try {
            readSource(staged);
            if (staged.length() == 0) throw new IOException("Empty wallpaper source");
            HttpCallScope.throwIfCancelled();
            resize(staged);
            HttpCallScope.throwIfCancelled();
            if (staged.length() == 0) throw new IOException("Empty processed wallpaper");
            if (!staged.renameTo(file)) throw new IOException("Cannot replace wallpaper");
            return file;
        } finally {
            if (staged.exists()) staged.delete();
        }
    }

    void readSource(File file) throws Exception {
        String url = getUrl();
        if (url.startsWith("file")) {
            if (!Path.tryCopy(Path.local(url), file)) throw new IOException("Cannot read wallpaper file");
        } else if (url.startsWith("assets")) {
            if (!Path.tryCopy(Asset.open(url), file)) throw new IOException("Cannot read wallpaper asset");
        } else if (url.startsWith("http")) Download.create(url, file).sync();
        else throw new IOException("Unsupported wallpaper source");
    }

    File resize(File file) throws Exception {
        RequestManager manager = Glide.with(App.get());
        FutureTarget<Bitmap> target = manager.asBitmap().load(file).centerCrop().override(ResUtil.getScreenWidth(), ResUtil.getScreenHeight()).skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE).submit();
        try {
            Bitmap bitmap = target.get();
            try (FileOutputStream output = new FileOutputStream(file)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)) throw new IOException("Cannot encode wallpaper");
            }
            return file;
        } finally {
            manager.clear(target);
        }
    }

    public boolean needSync(String url) {
        Config config = getConfig();
        return sync || TextUtils.isEmpty(config.getUrl()) || url.equals(config.getUrl());
    }

    public static void refresh(int index) {
        Setting.putWall(index);
        RefreshEvent.wall();
    }
}
