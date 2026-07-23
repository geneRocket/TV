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
        if (result.hasMsg()) {
            Notify.show(result.getMsg());
        }  else {
            download(activity);
        }
        clear();
    }

    private void download(Activity activity) {
        download(activity, result.getHeaders(), result.getRealUrl());
    }

    private void download(Activity activity, Map<String, String> headers, String url) {
        IDMUtil.downloadFile(activity, UrlUtil.fixDownloadUrl(url), title, headers, false, false);
    }

    private void clear() {
        result = null;
        title = null;
    }

}
