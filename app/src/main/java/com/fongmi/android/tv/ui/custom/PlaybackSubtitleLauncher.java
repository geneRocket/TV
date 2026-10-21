package com.fongmi.android.tv.ui.custom;

import androidx.fragment.app.FragmentActivity;

import androidx.media3.ui.SubtitleView;

import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.utils.CallbackScope;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Shared subtitle UI launch/result boundary, scoped to the originating media request. */
public final class PlaybackSubtitleLauncher {
    private PlaybackSubtitleLauncher() {}

    public static String name(String name, String episode, CharSequence title) {
        if (name != null && !name.isEmpty()) return name;
        if (episode != null && !episode.isEmpty()) return episode;
        return title == null ? "" : title.toString();
    }

    public static void show(FragmentActivity activity, CallbackScope callbacks, SubtitleView view, String name,
                            boolean full, BooleanSupplier accepts, Consumer<Sub> selected) {
        callbacks.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed() || !accepts.getAsBoolean()) return;
            SubtitleDialog.create().view(view).name(name).full(full).listener(subtitle -> {
                if (!activity.isFinishing() && !activity.isDestroyed() && accepts.getAsBoolean())
                    selected.accept(Sub.from(subtitle.getUrl()));
            }).show(activity);
        }, 200);
    }
}
