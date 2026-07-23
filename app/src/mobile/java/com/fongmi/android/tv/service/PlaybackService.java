package com.fongmi.android.tv.service;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.util.LruCache;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.receiver.ActionReceiver;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class PlaybackService extends Service {

    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(8 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value == null ? 0 : Math.max(value.getByteCount() / 1024, 1);
        }
    };
    private final Set<String> loadingArtwork = Collections.synchronizedSet(new HashSet<>());
    private final Set<CustomTarget<Bitmap>> artworkTargets = Collections.synchronizedSet(new HashSet<>());
    private static volatile Players player;
    private volatile boolean destroyed;

    public static void start(Players player) {
        PlaybackService.player = player;
        ContextCompat.startForegroundService(App.get(), new Intent(App.get(), PlaybackService.class));
    }

    public static void stop() {
        App.get().stopService(new Intent(App.get(), PlaybackService.class));
    }

    private boolean isNull() {
        return Objects.isNull(player) || Objects.isNull(player.getSession());
    }

    private boolean nonNull() {
        return Objects.nonNull(player) && Objects.nonNull(player.getSession());
    }

    private NotificationManagerCompat getManager() {
        return NotificationManagerCompat.from(this);
    }

    private NotificationCompat.Action buildNotificationAction(@DrawableRes int icon, @StringRes int title, String action) {
        return new NotificationCompat.Action(icon, getString(title), ActionReceiver.getPendingIntent(this, action));
    }

    private NotificationCompat.Action getPlayPauseAction() {
        if (nonNull() && player.isPlaying()) return buildNotificationAction(R.drawable.ic_notify_pause, androidx.media3.ui.R.string.exo_controls_pause_description, ActionEvent.PAUSE);
        return buildNotificationAction(R.drawable.ic_notify_play, androidx.media3.ui.R.string.exo_controls_play_description, ActionEvent.PLAY);
    }

    private MediaMetadataCompat getMetadata() {
        return isNull() ? null : player.getSession().getController().getMetadata();
    }

    private String getTitle() {
        if (getMetadata() == null) return null;
        String title = getMetadata().getString(MediaMetadataCompat.METADATA_KEY_TITLE);
        return title == null || title.isEmpty() ? null : title;
    }

    private String getArtist() {
        if (getMetadata() == null) return null;
        String artist = getMetadata().getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
        return artist == null || artist.isEmpty() ? null : artist;
    }

    private String getArtUri() {
        return getMetadata() == null ? "" : getMetadata().getString(MediaMetadataCompat.METADATA_KEY_ART_URI);
    }

    private void setLargeIcon(NotificationCompat.Builder builder, Bitmap art) {
        Bitmap b1 = Bitmap.createScaledBitmap(art, 16, 16, true);
        Bitmap b2 = Bitmap.createScaledBitmap(b1, 1, 1, true);
        builder.setColor(b2.getPixel(0, 0));
        builder.setLargeIcon(art);
        b2.recycle();
        b1.recycle();
    }

    private void setArtwork(NotificationCompat.Builder builder) {
        String artUri = getArtUri();
        if (artUri == null || artUri.isEmpty()) return;
        Bitmap cached = cache.get(artUri);
        if (cached != null) {
            setLargeIcon(builder, cached);
        } else {
            if (loadingArtwork.contains(artUri)) return;
            loadingArtwork.add(artUri);
            CustomTarget<Bitmap> target = getCallback(builder, artUri);
            artworkTargets.add(target);
            ImgUtil.load(artUri, target);
        }
    }

    private void addAction(NotificationCompat.Builder builder) {
        builder.addAction(buildNotificationAction(R.drawable.ic_notify_prev, androidx.media3.ui.R.string.exo_controls_previous_description, ActionEvent.PREV));
        builder.addAction(getPlayPauseAction());
        builder.addAction(buildNotificationAction(R.drawable.ic_notify_next, androidx.media3.ui.R.string.exo_controls_next_description, ActionEvent.NEXT));
    }

    private Notification buildNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Notify.DEFAULT);
        builder.setOngoing(false);
        builder.setColorized(true);
        builder.setOnlyAlertOnce(true);
        builder.setContentText(getArtist());
        builder.setContentTitle(getTitle());
        builder.setSmallIcon(R.drawable.ic_logo);
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        builder.setDeleteIntent(ActionReceiver.getPendingIntent(this, ActionEvent.STOP));
        if (nonNull()) builder.setContentIntent(player.getSession().getController().getSessionActivity());
        if (nonNull()) builder.setStyle(new MediaStyle().setMediaSession(player.getSession().getSessionToken()));
        addAction(builder);
        setArtwork(builder);
        return builder.build();
    }

    private CustomTarget<Bitmap> getCallback(NotificationCompat.Builder builder, String artUri) {
        return new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                loadingArtwork.remove(artUri);
                artworkTargets.remove(this);
                if (destroyed) return;
                cache.put(artUri, resource);
                setLargeIcon(builder, resource);
                Notify.show(builder.build());
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
                loadingArtwork.remove(artUri);
                artworkTargets.remove(this);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                loadingArtwork.remove(artUri);
                artworkTargets.remove(this);
            }
        };
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActionEvent(ActionEvent event) {
        if (event.isUpdate()) Notify.show(buildNotification());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        destroyed = false;
        EventBus.getDefault().register(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (nonNull()) MediaButtonReceiver.handleIntent(player.getSession(), intent);
        startForeground(Notify.ID, buildNotification());
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopSelf();
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        EventBus.getDefault().unregister(this);
        getManager().cancel(Notify.ID);
        stopForeground(true);
        synchronized (artworkTargets) {
            List<CustomTarget<Bitmap>> targets = new ArrayList<>(artworkTargets);
            artworkTargets.clear();
            for (CustomTarget<Bitmap> target : targets) Glide.with(App.get()).clear(target);
        }
        cache.evictAll();
        loadingArtwork.clear();
        player = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
