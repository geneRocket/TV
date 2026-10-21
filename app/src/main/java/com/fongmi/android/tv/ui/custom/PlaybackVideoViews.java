package com.fongmi.android.tv.ui.custom;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.player.PlaybackController;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

/** Renders the selected engine and transport labels without initiating playback or changing preferences. */
public final class PlaybackVideoViews {
    private PlaybackVideoViews() {}

    public static void render(PlaybackController player, PlayerView exo, IjkVideoView ijk, TextView playerLabel, View speedControl) {
        ijk.setPlayer(player.getPlayer());
        text(playerLabel, player.getPlayerText());
        speedControl.setEnabled(player.canAdjustSpeed());
        visible(exo, player.isExo());
        visible(ijk, player.isIjk());
    }

    private static void visible(View view, boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (view.getVisibility() != visibility) view.setVisibility(visibility);
    }

    private static void text(TextView view, CharSequence value) {
        if (!TextUtils.equals(view.getText(), value)) view.setText(value);
    }
}
