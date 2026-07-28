package com.fongmi.quickjs.utils;

import android.net.Uri;
import android.system.Os;
import android.util.Base64;

import com.fongmi.android.tv.utils.BoundedCache;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Asset;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;
import com.google.common.net.HttpHeaders;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Headers;

public class Module {

    private static final long CACHE_TTL = 15 * 60 * 1000L;
    private static final long RETRY_INTERVAL = 60 * 1000L;
    private static final int REFRESH_THREADS = 5;
    private static final int MODULE_CACHE_BYTES = 4 * 1024 * 1024;
    private static final int REFRESH_QUEUE_SIZE = 128;
    private static final int ATTEMPT_CACHE_SIZE = 256;

    private final BoundedCache<String, String> cache;
    private final BoundedCache<String, Long> attempts;
    private final Set<String> refreshing;
    private final ExecutorService executor;

    private static class Loader {
        static volatile Module INSTANCE = new Module();
    }

    public static Module get() {
        return Loader.INSTANCE;
    }

    public Module() {
        this.cache = new BoundedCache<>(MODULE_CACHE_BYTES, value -> value.length() * Character.BYTES);
        this.attempts = new BoundedCache<>(ATTEMPT_CACHE_SIZE);
        this.refreshing = ConcurrentHashMap.newKeySet();
        this.executor = createExecutor();
    }

    private ExecutorService createExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(REFRESH_THREADS, REFRESH_THREADS, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(REFRESH_QUEUE_SIZE), new RefreshThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static final class RefreshThreadFactory implements ThreadFactory {

        private final AtomicInteger number = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, "quickjs-module-" + number.getAndIncrement());
        }
    }

    public String fetch(String name) {
        String cached = cache.get(name);
        if (cached != null) {
            if (name.startsWith("http")) refresh(name);
            return cached;
        }
        String content = load(name);
        if (!content.isEmpty()) cache.put(name, content);
        return content;
    }

    public String getCached(String name) {
        return cache.get(name);
    }

    public boolean isRemote(String name) {
        return name != null && (name.startsWith("http://") || name.startsWith("https://"));
    }

    private String load(String name) {
        if (name.startsWith("http")) return request(name);
        if (name.startsWith("assets")) return Asset.read(name);
        if (name.startsWith("lib/")) return Asset.read("js/" + name);
        return "";
    }

    private String request(String url) {
        String cached = cache(url);
        if (!cached.isEmpty()) {
            refresh(url);
            return cached;
        }
        return download(url);
    }

    private String download(String url) {
        try {
            Uri uri = Uri.parse(url);
            File file = file(url);
            boolean cacheable = !"127.0.0.1".equals(uri.getHost());
            try (okhttp3.Response response = OkHttp.newCall(url, Headers.of(HttpHeaders.USER_AGENT, "Mozilla/5.0")).execute()) {
                if (!response.isSuccessful() || response.body() == null) return cache(url);
                byte[] data = response.body().bytes();
                if (data.length == 0) return cache(url);
                String content = new String(data, StandardCharsets.UTF_8);
                if (cacheable) write(file, data);
                return content;
            }
        } catch (Exception e) {
            return cache(url);
        }
    }

    private void refresh(String url) {
        File file = file(url);
        long now = System.currentTimeMillis();
        if (file.exists() && now - file.lastModified() < CACHE_TTL) return;
        Long attempt = attempts.get(url);
        if (attempt != null && now - attempt < RETRY_INTERVAL) return;
        if (!refreshing.add(url)) return;
        attempts.put(url, now);
        try {
            executor.execute(() -> {
                try {
                    String content = download(url);
                    if (!content.isEmpty()) cache.put(url, content);
                } finally {
                    refreshing.remove(url);
                }
            });
        } catch (RejectedExecutionException ignored) {
            refreshing.remove(url);
        }
    }

    private void write(File file, byte[] data) {
        File temp = new File(file.getParentFile(), file.getName() + "." + Thread.currentThread().getId() + ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temp)) {
                output.write(data);
                output.flush();
                output.getFD().sync();
            }
            Os.rename(temp.getAbsolutePath(), file.getAbsolutePath());
        } catch (Exception ignored) {
        } finally {
            if (temp.exists()) temp.delete();
        }
    }

    private String cache(String url) {
        try {
            File file = file(url);
            return file.exists() ? Path.read(file) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private File file(String url) {
        return Path.js(Util.md5(url).concat(".js"));
    }

    public byte[] bb(String content) {
        byte[] bytes = Base64.decode(content.substring(4), Base64.DEFAULT);
        byte[] newBytes = new byte[bytes.length - 4];
        newBytes[0] = 1;
        System.arraycopy(bytes, 5, newBytes, 1, bytes.length - 5);
        return newBytes;
    }
}
