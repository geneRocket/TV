package com.fongmi.android.tv.receiver;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.player.PlaybackController;
import com.fongmi.android.tv.player.PlaybackOwners;

public class ActionReceiver extends BroadcastReceiver {

    public static PendingIntent getPendingIntent(Context context, String action) {
        return getPendingIntent(context, action, PlaybackOwners.current());
    }

    public static PendingIntent getPendingIntent(Context context, String action, PlaybackController owner) {
        return getPendingIntent(context, action, owner, false);
    }

    public static PendingIntent getNotificationDismissIntent(Context context, PlaybackController owner) {
        return getPendingIntent(context, ActionEvent.STOP, owner, true);
    }

    private static PendingIntent getPendingIntent(Context context, String action, PlaybackController owner, boolean dismissal) {
        long lease = PlaybackOwners.lease(owner);
        Intent intent = new Intent(context, ActionReceiver.class).setAction(action)
                .setData(Uri.parse("playback://owner/" + lease + "/" + action + (dismissal ? "/dismiss" : "/control")))
                .putExtra("playback_owner", lease).putExtra("notification_dismissal", dismissal);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) PlaybackOwners.dispatchSystem(intent.getLongExtra("playback_owner", 0), ActionEvent.input(intent.getAction()),
                intent.getBooleanExtra("notification_dismissal", false));
    }
}
