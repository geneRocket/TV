package com.fongmi.android.tv.utils;

import android.widget.TextView;

import com.fongmi.android.tv.App;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class Clock {

    private SimpleDateFormat format;
    private Callback callback;
    private final Date date;
    private final Runnable ticker;
    private List<TextView> views;
    private Timer timer;

    public static Clock create() {
        return new Clock();
    }

    public static Clock create(TextView view) {
        return new Clock().view(view).format("HH:mm:ss");
    }

    public static Clock create(List<TextView> views) {
        return new Clock().view(views).format("HH:mm:ss");
    }

    public Clock() {
        this.date = new Date();
        this.ticker = this::doJob;
        this.views = new ArrayList<>();
        this.format = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    }

    public Clock view(TextView view) {
        this.views.add(view);
        return this;
    }

    public Clock view(List<TextView> views) {
        this.views = new ArrayList<>(views);
        return this;
    }

    public Clock format(String format) {
        this.format = new SimpleDateFormat(format, Locale.getDefault());
        return this;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void start() {
        stop();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                App.post(ticker);
            }
        }, 0, 1000);
    }

    private void doJob() {
        try {
            date.setTime(System.currentTimeMillis());
            if (callback != null) callback.onTimeChanged();
            for(TextView view : views) {
                if (view != null) view.setText(format.format(date));
            }
        } catch (Exception ignored) {
        }
    }

    public Clock stop() {
        if (timer != null) timer.cancel();
        timer = null;
        App.removeCallbacks(ticker);
        return this;
    }

    public void release() {
        stop();
        callback = null;
        views.clear();
    }

    public interface Callback {

        void onTimeChanged();
    }
}
