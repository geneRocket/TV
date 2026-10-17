package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Device;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ScanTaskDeliveryTest {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Scheduler scheduler = new Scheduler();
    private final List<List<Device>> delivered = new ArrayList<>();
    private final List<Device> devices = Collections.synchronizedList(new ArrayList<>());
    private final ScanTask task = new ScanTask(delivered::add, new OkHttpClient(), scheduler);

    private void ownScan() throws Exception {
        Field owner = ScanTask.class.getDeclaredField("executor"); owner.setAccessible(true); owner.set(task, executor);
        Field generation = ScanTask.class.getDeclaredField("generation"); generation.setAccessible(true); generation.setLong(task, 1);
    }

    @After public void release() { task.stop(); executor.shutdownNow(); }

    @Test(timeout = 5000) public void firstDeviceIsDeliveredWhileAnotherProbeIsStillRunning() throws Exception {
        ownScan();
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        executor.submit(() -> {
            started.countDown();
            try { release.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        });
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS));
            Device found = new Device();
            task.recordDevice(executor, 1, devices, found);
            scheduler.drain();
            assertEquals(1, delivered.size());
            assertSame(found, delivered.get(0).get(0));
            assertFalse(executor.isShutdown());
            assertEquals(1, release.getCount());
        } finally { release.countDown(); }
    }

    @Test public void stoppingScanSuppressesAlreadyQueuedDiscovery() throws Exception {
        ownScan();
        task.recordDevice(executor, 1, devices, new Device());
        assertFalse(scheduler.queue.isEmpty());
        Runnable stale = scheduler.queue.get(0);
        task.stop();
        stale.run();
        assertTrue(delivered.isEmpty());
    }

    @Test public void burstFindsCoalesceAndDeliverAnIndependentSnapshot() throws Exception {
        ownScan();
        task.recordDevice(executor, 1, devices, new Device());
        task.recordDevice(executor, 1, devices, new Device());
        assertEquals(1, scheduler.queue.size());
        scheduler.drain();
        assertEquals(1, delivered.size());
        assertEquals(2, delivered.get(0).size());
        task.recordDevice(executor, 1, devices, new Device());
        assertEquals(2, delivered.get(0).size());
        scheduler.drain();
        assertEquals(3, delivered.get(1).size());
    }

    @Test public void staleScanCannotAddOrPublishDevice() throws Exception {
        ownScan();
        task.recordDevice(executor, 0, devices, new Device());
        assertTrue(devices.isEmpty());
        assertTrue(scheduler.queue.isEmpty());
    }

    private static class Scheduler implements TaskScheduler {
        final List<Runnable> queue = new ArrayList<>();
        public void post(Runnable task, long delayMillis) { queue.add(task); }
        public void remove(Runnable task) { queue.remove(task); }
        void drain() { while (!queue.isEmpty()) queue.remove(0).run(); }
    }
}
