package com.fongmi.android.tv.utils;

public interface TaskScheduler {

    void post(Runnable task, long delayMillis);

    void remove(Runnable task);
}
