package com.fongmi.android.tv.service;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.android.cast.dlna.dmr.DLNARendererService;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Util;

public class StableDLNARendererService extends DLNARendererService {

    private static final String EXTRA_ICON = "icon";
    private static final int NOTIFICATION_ID = 9528;
    private WifiManager.MulticastLock lock;
    private WifiManager.WifiLock wifiLock;

    public static void start(Context context, int icon) {
        Intent intent = new Intent(context, StableDLNARendererService.class);
        intent.setAction("com.android.cast.dlna.dmr.ACTION_CAST");
        intent.putExtra(EXTRA_ICON, icon);
        Context app = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(intent);
        else app.startService(intent);
    }

    @Override
    public void onCreate() {
        acquireLock();
        startForeground(NOTIFICATION_ID, buildNotification(R.drawable.ic_logo));
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int icon = intent == null ? R.drawable.ic_logo : intent.getIntExtra(EXTRA_ICON, R.drawable.ic_logo);
        startForeground(NOTIFICATION_ID, buildNotification(icon));
        return super.onStartCommand(intent, flags, startId);
    }

    private void acquireLock() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return;
            lock = wm.createMulticastLock("TV");
            lock.setReferenceCounted(true);
            lock.acquire();
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "TV");
            wifiLock.acquire();
        } catch (Exception ignored) {
        }
    }

    private void releaseLock() {
        try {
            if (lock != null && lock.isHeld()) lock.release();
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseLock();
    }

    private Notification buildNotification(int icon) {
        int smallIcon = icon == 0 ? R.drawable.ic_logo : icon;
        return new NotificationCompat.Builder(this, Notify.DEFAULT)
                .setSmallIcon(smallIcon)
                .setContentTitle("DLNA")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
