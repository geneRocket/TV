package com.fongmi.android.tv.db;

import com.fongmi.android.tv.bean.History;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class DatabaseResetConcurrencyTest {
    @Test public void waitingForHistoryWritesDoesNotHoldTheDatabaseMonitor() throws Exception {
        Field field = History.class.getDeclaredField("WRITE_LOCK");
        field.setAccessible(true);
        Object writes = field.get(null);
        CountDownLatch databaseAvailable = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread reset = new Thread(() -> {
            try { AppDatabase.reset(); }
            catch (Throwable failure) { error.set(failure); }
        }, "reset-waiting-for-history");
        Thread reader = new Thread(() -> {
            synchronized (AppDatabase.class) { databaseAvailable.countDown(); }
        }, "history-needs-database");
        boolean available;
        try {
            synchronized (writes) {
                reset.start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean blockedOnWrites = false;
                while (System.nanoTime() < deadline) {
                    if (reset.getState() == Thread.State.BLOCKED) {
                        blockedOnWrites = true;
                        break;
                    }
                    Thread.yield();
                }
                assertTrue("Reset never reached the history write barrier", blockedOnWrites);
                reader.start();
                available = databaseAvailable.await(2, TimeUnit.SECONDS);
            }
        } finally {
            reset.join(5000);
            if (reader.getState() != Thread.State.NEW) reader.join(5000);
        }
        assertNull(error.get());
        assertFalse(reset.isAlive());
        assertFalse(reader.isAlive());
        assertTrue("Reset holds the database monitor while waiting for a history writer that needs it", available);
    }
}
