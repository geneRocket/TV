package com.fongmi.android.tv.player.extractor;

import android.net.Uri;
import com.fongmi.android.tv.player.Source;

public class Push implements Source.Extractor {

    @Override
    public boolean match(Uri uri) {
        return "push".equals(uri.getScheme());
    }

    @Override
    public String fetch(String url) {
        if (url.startsWith("push://")) return url.substring("push://".length());
        if (url.startsWith("push:")) return url.substring("push:".length());
        return url;
    }

    @Override
    public void stop() {
    }

    @Override
    public void exit() {
    }
}
