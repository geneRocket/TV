package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.repository.DeviceRepository;
import com.fongmi.android.tv.server.Server;
import com.github.catvod.net.OkHttp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import okhttp3.OkHttpClient;
import okhttp3.Response;

public class ScanTask {

    private final Listener listener;
    private final OkHttpClient client;
    private volatile ExecutorService executor;
    private volatile long generation;

    public static ScanTask create(Listener listener) {
        return new ScanTask(listener);
    }

    public ScanTask(Listener listener) {
        this.listener = listener;
        this.client = OkHttp.client(1000);
    }

    public synchronized void start(List<String> ips) {
        begin(getUrl(ips));
    }

    public synchronized void start(String url) {
        begin(Arrays.asList(url));
    }

    public synchronized void stop() {
        generation++;
        if (executor != null) executor.shutdownNow();
        executor = null;
    }

    private void begin(List<String> urls) {
        stop();
        long currentGeneration = ++generation;
        List<Device> devices = new ArrayList<>();
        ExecutorService currentExecutor = ThreadPools.newFixed("scan", Constant.THREAD_POOL);
        executor = currentExecutor;
        currentExecutor.execute(() -> run(urls, currentExecutor, currentGeneration, devices));
    }

    private void run(List<String> items, ExecutorService currentExecutor, long currentGeneration, List<Device> devices) {
        boolean current;
        try {
            getDevice(items, currentExecutor, currentGeneration, devices);
        } catch (Exception e) {
            ThreadPools.log(e, "Scan task failed.");
        } finally {
            synchronized (this) {
                current = isCurrent(currentExecutor, currentGeneration);
                if (current) {
                    ThreadPools.shutdown(currentExecutor);
                    executor = null;
                }
            }
            if (current) App.post(() -> {
                if (generation == currentGeneration) listener.onFind(new ArrayList<>(devices));
            });
        }
    }

    private void getDevice(List<String> urls, ExecutorService currentExecutor, long currentGeneration, List<Device> devices) throws Exception {
        CountDownLatch cd = new CountDownLatch(urls.size());
        for (String url : urls) {
            if (!isCurrent(currentExecutor, currentGeneration)) {
                cd.countDown();
                continue;
            }
            try {
                currentExecutor.execute(() -> findDevice(cd, url, currentExecutor, currentGeneration, devices));
            } catch (RuntimeException e) {
                cd.countDown();
            }
        }
        cd.await();
    }

    private List<String> getUrl(List<String> ips) {
        LinkedHashSet<String> urls = new LinkedHashSet<>(ips);
        String local = Server.get().getAddress();
        String base = local.substring(0, local.lastIndexOf(".") + 1);
        for (int i = 1; i < 256; i++) urls.add(base + i + ":9978");
        return new ArrayList<>(urls);
    }

    private boolean isCurrent(ExecutorService currentExecutor, long currentGeneration) {
        return executor == currentExecutor && generation == currentGeneration;
    }

    private void findDevice(CountDownLatch cd, String url, ExecutorService currentExecutor, long currentGeneration, List<Device> devices) {
        try {
            if (!isCurrent(currentExecutor, currentGeneration) || Thread.currentThread().isInterrupted()) return;
            if (url.contains(Server.get().getAddress())) return;
            String result;
            try (Response response = OkHttp.newCall(client, url.concat("/device")).execute()) {
                if (!response.isSuccessful()) return;
                result = response.body() == null ? "" : response.body().string();
            }
            Device device = DeviceRepository.get().fromJson(result);
            if (device == null || !isCurrent(currentExecutor, currentGeneration)) return;
            devices.add(device.save());
        } catch (Exception ignored) {
        } finally {
            cd.countDown();
        }
    }

    public interface Listener {

        void onFind(List<Device> devices);
    }
}
