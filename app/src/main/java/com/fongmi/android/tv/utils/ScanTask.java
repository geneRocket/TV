package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.repository.DeviceRepository;
import com.fongmi.android.tv.server.Server;
import com.github.catvod.net.OkHttp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import okhttp3.Call;
import java.util.concurrent.ExecutorService;

import okhttp3.OkHttpClient;
import okhttp3.Response;

public class ScanTask {

    private final Listener listener;
    private final OkHttpClient client;
    private final TaskScheduler scheduler;
    private volatile ExecutorService executor;
    private volatile Runnable completion;
    private volatile long generation;
    private final Set<Call> calls = ConcurrentHashMap.newKeySet();

    public static ScanTask create(Listener listener) {
        return new ScanTask(listener);
    }

    public ScanTask(Listener listener) {
        this(listener, OkHttp.client(1000), AppTaskScheduler.get());
    }

    ScanTask(Listener listener, OkHttpClient client, TaskScheduler scheduler) {
        this.listener = listener;
        this.client = client;
        this.scheduler = scheduler;
    }

    public synchronized void start(List<String> ips) {
        begin(getUrl(ips));
    }

    public synchronized void start(String url) {
        begin(Arrays.asList(url));
    }

    public synchronized void stop() {
        generation++;
        if (completion != null) scheduler.remove(completion);
        completion = null;
        for (Call call : calls) call.cancel();
        calls.clear();
        if (executor != null) executor.shutdownNow();
        executor = null;
    }

    private void begin(List<String> urls) {
        stop();
        long currentGeneration = ++generation;
        List<Device> devices = Collections.synchronizedList(new ArrayList<>());
        ExecutorService currentExecutor = ThreadPools.newFixed("scan", ThreadPools.searchConcurrency());
        executor = currentExecutor;
        currentExecutor.execute(() -> enqueue(urls, currentExecutor, currentGeneration, devices));
    }

    private void enqueue(List<String> urls, ExecutorService currentExecutor, long currentGeneration, List<Device> devices) {
        AtomicInteger remaining = new AtomicInteger(urls.size());
        if (urls.isEmpty()) finish(currentExecutor, currentGeneration, devices);
        for (String url : urls) {
            if (!isCurrent(currentExecutor, currentGeneration)) return;
            try {
                currentExecutor.execute(() -> {
                    try { findDevice(url, currentExecutor, currentGeneration, devices); }
                    finally {
                        if (remaining.decrementAndGet() == 0) finish(currentExecutor, currentGeneration, devices);
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException error) {
                if (remaining.decrementAndGet() == 0) finish(currentExecutor, currentGeneration, devices);
            }
        }
    }

    private synchronized void finish(ExecutorService currentExecutor, long currentGeneration, List<Device> devices) {
        if (!isCurrent(currentExecutor, currentGeneration)) return;
        currentExecutor.shutdown();
        executor = null;
        scheduleComplete(currentGeneration, devices);
    }

    private synchronized void scheduleComplete(long currentGeneration, List<Device> devices) {
        if (generation != currentGeneration) return;
        if (completion != null) scheduler.remove(completion);
        Runnable delivery = () -> complete(currentGeneration, devices);
        completion = delivery;
        scheduler.post(delivery, 0);
    }

    private void complete(long currentGeneration, List<Device> devices) {
        synchronized (this) {
            if (generation != currentGeneration) return;
            if (completion != null) scheduler.remove(completion);
            completion = null;
        }
        listener.onFind(new ArrayList<>(devices));
    }

    private List<String> getUrl(List<String> ips) {
        LinkedHashSet<String> urls = new LinkedHashSet<>(ips == null ? Collections.emptyList() : ips);
        String local = Server.get().getAddress();
        String base = local.substring(0, local.lastIndexOf(".") + 1);
        for (int i = 1; i < 256; i++) urls.add(base + i + ":9978");
        return new ArrayList<>(urls);
    }

    private boolean isCurrent(ExecutorService currentExecutor, long currentGeneration) {
        return executor == currentExecutor && generation == currentGeneration;
    }

    synchronized void recordDevice(ExecutorService currentExecutor, long currentGeneration, List<Device> devices, Device device) {
        if (!isCurrent(currentExecutor, currentGeneration)) return;
        devices.add(device);
        scheduleComplete(currentGeneration, devices);
    }

    private void findDevice(String url, ExecutorService currentExecutor, long currentGeneration, List<Device> devices) {
        Call call = null;
        try {
            if (!isCurrent(currentExecutor, currentGeneration) || Thread.currentThread().isInterrupted()) return;
            if (url.contains(Server.get().getAddress())) return;
            String result;
            call = OkHttp.newCall(client, url.concat("/device"));
            calls.add(call);
            if (!isCurrent(currentExecutor, currentGeneration) || Thread.currentThread().isInterrupted()) call.cancel();
            try (Response response = call.execute()) {
                if (!response.isSuccessful()) return;
                result = response.body() == null ? "" : response.body().string();
            }
            Device device = DeviceRepository.get().fromJson(result);
            if (device == null || !isCurrent(currentExecutor, currentGeneration)) return;
            recordDevice(currentExecutor, currentGeneration, devices, device.save());
        } catch (Exception ignored) {
        } finally {
            if (call != null) calls.remove(call);
        }
    }

    public interface Listener {

        /** Receives main-thread snapshots as devices arrive, and once when the scan finishes. */
        void onFind(List<Device> devices);
    }
}
