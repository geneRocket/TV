package com.fongmi.android.tv.repository;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Resolves persisted entries without discarding the currently selected configurations. */
public final class VodEntryResolver {
    interface Backend {
        boolean hasSite(String key);
        Config find(int cid);
        List<Config> selected();
        void execute(Runnable task);
        void post(Runnable task);
        void load(List<Config> configs, Callback callback);
        void changed();
    }

    private final BooleanSupplier ready;
    private final Backend backend;
    private long generation;

    public VodEntryResolver(BooleanSupplier ready) {
        this(ready, new ProductionBackend());
    }

    VodEntryResolver(BooleanSupplier ready, Backend backend) {
        this.ready = ready;
        this.backend = backend;
    }

    public void cancel() {
        generation++;
    }

    private boolean current(long request) {
        return request == generation && ready.getAsBoolean();
    }

    public void open(int cid, String key, Runnable play, Runnable fallback, Consumer<String> error) {
        long request = ++generation;
        if (!current(request)) return;
        if (backend.hasSite(key)) {
            play.run();
            return;
        }
        backend.execute(() -> {
            try {
                Config owner = backend.find(cid);
                List<Config> configs = new ArrayList<>();
                if (owner != null && owner.getType() == 0 && !owner.getUrl().isEmpty()) {
                    for (Config config : backend.selected()) {
                        if (config != null && config.getType() == 0 && !config.getUrl().isEmpty()) configs.add(config);
                    }
                    boolean selected = false;
                    for (Config config : configs) if (config.getUrl().equals(owner.getUrl())) selected = true;
                    if (!selected) configs.add(owner);
                }
                backend.post(() -> {
                    if (!current(request)) return;
                    if (configs.isEmpty()) {
                        fallback.run();
                        return;
                    }
                    backend.load(configs, new Callback() {
                        @Override public void success() {
                            backend.post(() -> {
                                if (current(request)) {
                                    if (backend.hasSite(key)) play.run();
                                    else fallback.run();
                                }
                                backend.changed();
                            });
                        }

                        @Override public void error(String message) {
                            backend.post(() -> {
                                if (current(request)) error.accept(message);
                            });
                        }
                    });
                });
            } catch (Exception failure) {
                backend.post(() -> {
                    if (current(request)) error.accept(failure.getMessage());
                });
            }
        });
    }

    private static final class ProductionBackend implements Backend {
        @Override public boolean hasSite(String key) { return VodConfig.get().hasSite(key); }
        @Override public Config find(int cid) { return ConfigRepository.get().find(cid); }
        @Override public List<Config> selected() { return ConfigRepository.get().selected(0); }
        @Override public void execute(Runnable task) { App.execute(task); }
        @Override public void post(Runnable task) { App.post(task); }

        @Override public void load(List<Config> configs, Callback callback) {
            List<String> urls = new ArrayList<>();
            for (Config config : configs) urls.add(config.getUrl());
            Setting.putVodConfigUrls(String.join("\n", urls));
            String desc = configs.get(0).getDesc();
            if (configs.size() == 2) desc += " + " + configs.get(1).getDesc();
            else if (configs.size() > 2) desc += " +" + (configs.size() - 1);
            Setting.putVodConfigDesc(desc);
            if (configs.size() == 1) VodConfig.load(configs.get(0), callback);
            else VodConfig.load(configs, callback);
        }

        @Override public void changed() {
            RefreshEvent.history();
            RefreshEvent.config();
            RefreshEvent.video();
        }
    }
}
