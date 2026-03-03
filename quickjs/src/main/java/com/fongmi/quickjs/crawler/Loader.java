package com.fongmi.quickjs.crawler;

import com.whl.quickjs.android.QuickJSLoader;

public class Loader {

    public Loader() {
        QuickJSLoader.init();
    }

    public Spider spider(String key, String api) throws Exception {
        return new Spider(key, api);
    }
}
