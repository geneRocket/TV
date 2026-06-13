package com.fongmi.android.tv.utils;

import android.net.TrafficStats;
import android.view.View;
import android.widget.TextView;

import java.text.DecimalFormat;

public class Traffic {

    private static final DecimalFormat format = new DecimalFormat("#.0");
    private static final String UNIT_KB = " KB/s";
    private static final String UNIT_MB = " MB/s";
    private static final long STALE_ZERO_MS = 1500;
    private static long lastTotalRxBytes;
    private static long lastTimeStamp;
    private static String lastSpeed = "0" + UNIT_KB;
    private static final long UPDATE_INTERVAL = 500;

    public static void setSpeed(TextView view) {
        String speed = getSpeed();
        if (speed == null) return;
        view.setText(speed);
        view.setVisibility(View.VISIBLE);
    }

    private static long currentRxBytes() {
        return TrafficStats.getTotalRxBytes();
    }

    private static synchronized String getSpeed() {
        long nowTimeStamp = System.currentTimeMillis();
        if (nowTimeStamp - lastTimeStamp < UPDATE_INTERVAL) return lastSpeed;
        long currentRxBytes = currentRxBytes();
        if (currentRxBytes == TrafficStats.UNSUPPORTED) return null;
        long nowTotalRxBytes = currentRxBytes / 1024;
        if (lastTimeStamp == 0) {
            lastTimeStamp = nowTimeStamp;
            lastTotalRxBytes = nowTotalRxBytes;
            return lastSpeed;
        }
        long elapsed = nowTimeStamp - lastTimeStamp;
        long delta = Math.max(0, nowTotalRxBytes - lastTotalRxBytes);
        long speed = delta * 1000 / elapsed;
        if (delta == 0 && elapsed >= STALE_ZERO_MS) speed = 0;
        lastTimeStamp = nowTimeStamp;
        lastTotalRxBytes = nowTotalRxBytes;
        lastSpeed = speed < 1000 ? speed + UNIT_KB : format.format(speed / 1024f) + UNIT_MB;
        return lastSpeed;
    }

    public static synchronized void reset() {
        lastTotalRxBytes = 0;
        lastTimeStamp = 0;
        lastSpeed = "0" + UNIT_KB;
    }
}
