package com.fongmi.android.tv.service;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.android.cast.dlna.dmr.DLNARendererService;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.Notify;

public class StableDLNARendererService extends DLNARendererService {

    private static final String EXTRA_ICON = "icon";
    private static final int NOTIFICATION_ID = 9528;
    private WifiManager.MulticastLock lock;

    public static void start(Context context, int icon) {
        Intent intent = new Intent(context, StableDLNARendererService.class).putExtra(EXTRA_ICON, icon);
        Context app = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(intent);
        else app.startService(intent);
    }

    @Override
    public void onCreate() {
        startForeground(NOTIFICATION_ID, buildNotification(R.drawable.ic_logo));
        super.onCreate();
        acquireLock();
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
        } catch (Exception ignored) {
        }
    }

    private void releaseLock() {
        try {
            if (lock != null && lock.isHeld()) lock.release();
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
