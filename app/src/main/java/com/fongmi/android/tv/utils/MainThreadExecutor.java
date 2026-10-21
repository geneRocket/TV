package com.fongmi.android.tv.utils;

import android.os.Looper;

import com.fongmi.android.tv.App;

import java.util.concurrent.Executor;

/** Dispatches cross-thread callbacks without delaying inputs already on the UI thread. */
public final class MainThreadExecutor implements Executor {
    private static final MainThreadExecutor INSTANCE = new MainThreadExecutor();
    public static MainThreadExecutor get() { return INSTANCE; }
    private MainThreadExecutor() {}
    @Override public void execute(Runnable task) {
        if (Looper.myLooper() == Looper.getMainLooper()) task.run();
        else App.post(task);
    }
}
