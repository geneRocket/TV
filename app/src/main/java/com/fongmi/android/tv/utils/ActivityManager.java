package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ActivityManager implements Application.ActivityLifecycleCallbacks {

    private static final ActivityManager INSTANCE = new ActivityManager();

    private volatile Activity activity;

    public static ActivityManager get() {
        return INSTANCE;
    }

    private ActivityManager() {
    }

    public void init(Application application) {
        application.registerActivityLifecycleCallbacks(this);
    }

    public Activity getActivity() {
        return activity;
    }

    private void setActivity(Activity activity) {
        this.activity = activity;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        setActivity(activity);
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        setActivity(activity);
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        setActivity(activity);
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (this.activity == activity) setActivity(null);
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        if (this.activity == activity) setActivity(null);
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if (this.activity == activity) setActivity(null);
    }
}
