package com.fongmi.android.tv.utils;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class PauseExecutor extends ThreadPoolExecutor {

    private final ReentrantLock pauseLock;
    private final Condition condition;
    private boolean isPaused;

    public PauseExecutor(int corePoolSize, int queueCapacity) {
        super(corePoolSize, corePoolSize, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(Math.max(1, queueCapacity)), ThreadPools.newThreadFactory("pause"));
        pauseLock = new ReentrantLock();
        condition = pauseLock.newCondition();
        allowCoreThreadTimeOut(true);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        pauseLock.lock();
        try {
            while (isPaused) condition.await();
        } catch (InterruptedException ie) {
            t.interrupt();
        } finally {
            pauseLock.unlock();
        }
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        ThreadPools.log(t, "Pause executor task failed.");
    }

    public void pause() {
        pauseLock.lock();
        try {
            isPaused = true;
        } finally {
            pauseLock.unlock();
        }
    }

    public void resume() {
        pauseLock.lock();
        try {
            isPaused = false;
            condition.signalAll();
        } finally {
            pauseLock.unlock();
        }
    }
}
