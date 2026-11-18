package com.fongmi.quickjs.utils;

import android.util.Base64;

import com.fongmi.android.tv.utils.BoundedCache;
import com.fongmi.android.tv.utils.RemoteContentValidator;
import com.github.catvod.net.HttpCallScope;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Asset;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;
import com.google.common.net.HttpHeaders;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import okhttp3.Call;
import okhttp3.Headers;

public class Module {

    private static final Object CACHE_WRITE_LOCK = new Object();

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
    private final Function<String, File> cacheFile;
    private final Function<String, Call> newCall;
    private volatile boolean closed;
    private final ConcurrentHashMap<String, CompletableFuture<String>> downloads = new ConcurrentHashMap<>();

    private static class Loader {
        static volatile Module INSTANCE = new Module();
    }

    public static Module get() {
        return Loader.INSTANCE;
    }

    public static void clearCache(Runnable clearDisk) {
        synchronized (CACHE_WRITE_LOCK) {
            Module old = Loader.INSTANCE;
            old.closed = true;
            old.executor.shutdownNow();
            old.cache.clear();
            old.attempts.clear();
            try { clearDisk.run(); }
            finally { Loader.INSTANCE = new Module(); }
        }
    }

    public Module() {
        this(createExecutor(), url -> Path.js(Util.md5(url).concat(".js")),
                url -> OkHttp.newCall(url, Headers.of(HttpHeaders.USER_AGENT, "Mozilla/5.0")));
    }

    Module(ExecutorService executor, Function<String, File> cacheFile, Function<String, Call> newCall) {
        this.cache = new BoundedCache<>(MODULE_CACHE_BYTES, value -> value.length() * Character.BYTES);
        this.attempts = new BoundedCache<>(ATTEMPT_CACHE_SIZE);
        this.refreshing = ConcurrentHashMap.newKeySet();
        this.executor = executor;
        this.cacheFile = cacheFile;
        this.newCall = newCall;
    }

    private static ExecutorService createExecutor() {
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
        if (name.startsWith("http")) return request(name);
        String cached = cache.get(name);
        if (cached != null) return cached;
        String content = loadAsset(name);
        synchronized (CACHE_WRITE_LOCK) {
            if (!closed && !content.isEmpty()) cache.put(name, content);
        }
        return content;
    }

    public String getCached(String name) {
        String content = cache.get(name);
        if (content != null && isRemote(name)) refresh(name);
        return content;
    }

    public boolean isRemote(String name) {
        return name != null && (name.startsWith("http://") || name.startsWith("https://"));
    }

    private String loadAsset(String name) {
        if (name.startsWith("assets")) return Asset.read(name);
        if (name.startsWith("lib/")) return Asset.read("js/" + name);
        return "";
    }

    private String request(String url) {
        String cached = cache.get(url);
        if (cached == null) {
            synchronized (CACHE_WRITE_LOCK) {
                if (closed) return "";
                cached = cache.get(url);
                if (cached == null) {
                    cached = cache(url);
                    if (!cached.isEmpty()) cache.put(url, cached);
                }
            }
        }
        if (!cached.isEmpty()) {
            refresh(url);
            return cached;
        }
        return download(url);
    }

    private String download(String url) {
        try {
            while (!closed) {
                HttpCallScope.throwIfCancelled();
                CompletableFuture<String> pending = new CompletableFuture<>();
                CompletableFuture<String> previous = downloads.putIfAbsent(url, pending);
                if (previous != null) {
                    try {
                        String content = previous.get();
                        HttpCallScope.throwIfCancelled();
                        return content;
                    } catch (CancellationException cancelled) {
                        // The previous download belonged to a different request.
                        continue;
                    } catch (ExecutionException error) {
                        return cache(url);
                    }
                }
                return downloadOwned(url, pending);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (InterruptedIOException cancelled) {
            // The caller's cancellation scope has closed.
        }
        return "";
    }

    private String downloadOwned(String url, CompletableFuture<String> pending) throws InterruptedIOException {
        try {
            String content = downloadContent(url);
            pending.complete(content);
            return content;
        } catch (InterruptedIOException cancelled) {
            downloads.remove(url, pending);
            pending.cancel(false);
            throw cancelled;
        } catch (RuntimeException | Error error) {
            pending.completeExceptionally(error);
            throw error;
        } finally {
            downloads.remove(url, pending);
        }
    }

    private String downloadContent(String url) throws InterruptedIOException {
        try {
            File file = file(url);
            Call call = newCall.apply(url);
            boolean cacheable = !"127.0.0.1".equals(call.request().url().host());
            try (okhttp3.Response response = call.execute()) {
                HttpCallScope.throwIfCancelled();
                if (!response.isSuccessful() || response.body() == null) return cache(url);
                byte[] data = response.body().bytes();
                if (data.length == 0) return cache(url);
                String content = new String(data, StandardCharsets.UTF_8);
                if (RemoteContentValidator.isHtml(content)) return cache(url);
                synchronized (CACHE_WRITE_LOCK) {
                    if (closed) return "";
                    HttpCallScope.throwIfCancelled();
                    if (cacheable) write(file, data);
                    cache.put(url, content);
                }
                return content;
            }
        } catch (Exception e) {
            HttpCallScope.throwIfCancelled();
            return cache(url);
        }
    }

    private void refresh(String url) {
        if (closed) return;
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
                    download(url);
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
            // Same-directory rename atomically replaces the path on Android/Linux.
            temp.renameTo(file);
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
        return cacheFile.apply(url);
    }

    public byte[] bb(String content) {
        byte[] bytes = Base64.decode(content.substring(4), Base64.DEFAULT);
        byte[] newBytes = new byte[bytes.length - 4];
        newBytes[0] = 1;
        System.arraycopy(bytes, 5, newBytes, 1, bytes.length - 5);
        return newBytes;
    }
}
