package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.server.Server;
import com.github.catvod.net.OkHttp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import okhttp3.OkHttpClient;
import okhttp3.Response;

public class ScanTask {

    private final Listener listener;
    private final OkHttpClient client;
    private final List<Device> devices;
    private ExecutorService executor;
    private volatile boolean stopped;

    public static ScanTask create(Listener listener) {
        return new ScanTask(listener);
    }

    public ScanTask(Listener listener) {
        this.listener = listener;
        this.client = OkHttp.client(1000);
        this.devices = Collections.synchronizedList(new ArrayList<>());
    }

    public synchronized void start(List<String> ips) {
        begin(getUrl(ips));
    }

    public synchronized void start(String url) {
        begin(Arrays.asList(url));
    }

    public synchronized void stop() {
        stopped = true;
        if (executor != null) executor.shutdownNow();
        executor = null;
    }

    private void begin(List<String> urls) {
        stop();
        stopped = false;
        devices.clear();
        ExecutorService currentExecutor = ThreadPools.newFixed("scan", Constant.THREAD_POOL);
        executor = currentExecutor;
        currentExecutor.execute(() -> run(urls, currentExecutor));
    }

    private void run(List<String> items, ExecutorService currentExecutor) {
        try {
            getDevice(items, currentExecutor);
        } catch (Exception e) {
            ThreadPools.log(e, "Scan task failed.");
        } finally {
            synchronized (this) {
                if (executor == currentExecutor) {
                    ThreadPools.shutdown(currentExecutor);
                    executor = null;
                }
            }
            App.post(() -> listener.onFind(new ArrayList<>(devices)));
        }
    }

    private void getDevice(List<String> urls, ExecutorService currentExecutor) throws Exception {
        CountDownLatch cd = new CountDownLatch(urls.size());
        for (String url : urls) {
            if (stopped) {
                cd.countDown();
                continue;
            }
            currentExecutor.execute(() -> findDevice(cd, url));
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

    private void findDevice(CountDownLatch cd, String url) {
        try {
            if (stopped || Thread.currentThread().isInterrupted()) return;
            if (url.contains(Server.get().getAddress())) return;
            String result;
            try (Response response = OkHttp.newCall(client, url.concat("/device")).execute()) {
                result = response.body() == null ? "" : response.body().string();
            }
            Device device = Device.objectFrom(result);
            if (device == null) return;
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
