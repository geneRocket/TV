package com.fongmi.android.tv.player;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.ThreadPools;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Encapsulates intents used to hand the current media item to another player. */
final class ExternalPlayer {

    private static final int REQUEST_CODE = 1001;

    private final Players players;

    ExternalPlayer(Players players) {
        this.players = players;
    }

    void share(Activity activity, CharSequence title) {
        try {
            if (players.isEmpty()) return;
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_TEXT, UrlUtil.fixDownloadUrl(players.getUrl()));
            intent.putExtra("extra_headers", getHeaderBundle());
            intent.putExtra("title", title);
            intent.putExtra("name", title);
            intent.setType("text/plain");
            activity.startActivity(Util.getChooser(intent));
        } catch (Exception e) {
            ThreadPools.log(e, "Share playback url failed.");
        }
    }

    void choose(Activity activity, CharSequence title) {
        try {
            if (players.isEmpty()) return;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(getUri(), "video/*");
            intent.putExtra("title", title);
            intent.putExtra("return_result", players.isVod());
            intent.putExtra("headers", getHeaderArray());
            if (players.isVod()) intent.putExtra("position", (int) players.getPosition());
            activity.startActivityForResult(Util.getChooser(intent), REQUEST_CODE);
        } catch (Exception e) {
            ThreadPools.log(e, "Open external player failed.");
        }
    }

    void checkResult(Intent data) {
        try {
            if (data == null || data.getExtras() == null) return;
            int position = data.getExtras().getInt("position", 0);
            String endBy = data.getExtras().getString("end_by", "");
            if ("playback_completion".equals(endBy)) ActionEvent.next();
            if ("user".equals(endBy)) players.seekTo(position);
        } catch (Exception e) {
            ThreadPools.log(e, "Handle external player result failed.");
        }
    }

    private Uri getUri() {
        String url = players.getUrl();
        return url.startsWith("file://") || url.startsWith("/") ? FileUtil.getShareUri(url) : Uri.parse(url);
    }

    private String[] getHeaderArray() {
        List<String> headers = new ArrayList<>();
        for (Map.Entry<String, String> entry : players.getHeaders().entrySet()) {
            headers.add(entry.getKey());
            headers.add(entry.getValue());
        }
        return headers.toArray(new String[0]);
    }

    private Bundle getHeaderBundle() {
        Bundle headers = new Bundle();
        for (Map.Entry<String, String> entry : players.getHeaders().entrySet()) headers.putString(entry.getKey(), entry.getValue());
        return headers;
    }
}
