package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;

/** Bridges latest-task callbacks to the application's main-thread scheduler. */
public final class AppTaskScheduler implements TaskScheduler {

    private static final AppTaskScheduler INSTANCE = new AppTaskScheduler();

    public static AppTaskScheduler get() {
        return INSTANCE;
    }

    private AppTaskScheduler() {
    }

    @Override
    public void post(Runnable task, long delayMillis) {
        App.post(task, delayMillis);
    }

    @Override
    public void remove(Runnable task) {
        App.removeCallbacks(task);
    }
}
