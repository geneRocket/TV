package com.fongmi.android.tv.player.extractor;

import android.net.Uri;
import android.os.SystemClock;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.ui.activity.VideoActivity;

public class Push implements Source.Extractor {

    @Override
    public boolean match(Uri uri) {
        return "push".equals(uri.getScheme());
    }

    @Override
    public String fetch(String url) throws Exception {
        if (App.activity() != null) {
            String pushUrl = url.startsWith("push://") ? url.substring("push://".length()) : url.startsWith("push:") ? url.substring("push:".length()) : url;
            VideoActivity.start(App.activity(), pushUrl);
        }
        SystemClock.sleep(500);
        return "";
    }

    @Override
    public void stop() {
    }

    @Override
    public void exit() {
    }
}
