package com.fongmi.android.tv.player.extractor;

import android.net.Uri;

import com.fongmi.android.tv.player.Source;

public class Video implements Source.Extractor {

    @Override
    public boolean match(Uri uri) {
        return "video".equals(uri.getScheme());
    }

    @Override
    public String fetch(String url) throws Exception {
        if (url.startsWith("video://")) return url.substring("video://".length());
        if (url.startsWith("video:")) return url.substring("video:".length());
        return url;
    }

    @Override
    public void stop() {
    }

    @Override
    public void exit() {
    }
}
