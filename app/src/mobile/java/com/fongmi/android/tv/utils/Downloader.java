package com.fongmi.android.tv.utils;

import android.app.Activity;
import com.fongmi.android.tv.bean.Result;

import java.util.Map;

public class Downloader {

    private Result result;
    private String title;

    public static Downloader create() {
        return new Downloader();
    }

    public Downloader title(String title) {
        this.title = title;
        return this;
    }
    public Downloader result(Result result) {
        this.result = result;
        return this;
    }

    public void start(Activity activity) {
        if (result == null || result.isRequestFailed()) {
            Notify.show(com.fongmi.android.tv.R.string.vod_load_failed);
        } else if (result.hasErrorMsg()) {
            Notify.show(result.getMsg());
        } else if (result.getRealUrl().isEmpty()) {
            Notify.show(com.fongmi.android.tv.R.string.vod_load_failed);
        } else if (!result.getRealUrl().startsWith("http")) {
            Notify.show(com.fongmi.android.tv.R.string.download_unsupported);
        }  else {
            download(activity);
        }
        clear();
    }

    private void download(Activity activity) {
        download(activity, result.getHeaders(), result.getRealUrl());
    }

    private void download(Activity activity, Map<String, String> headers, String url) {
        if (!IDMUtil.downloadFile(activity, UrlUtil.fixDownloadUrl(url), title, headers, false, false)) {
            Notify.show(com.fongmi.android.tv.R.string.download_launch_failed);
        }
    }

    private void clear() {
        result = null;
        title = null;
    }

}
