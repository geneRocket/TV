package com.fongmi.android.tv.utils;

import android.content.pm.PackageManager;
import android.webkit.CookieManager;

import com.fongmi.android.tv.App;

import java.util.Arrays;
import java.util.List;

public class WebViewUtil {

    private static final String SYSTEM_SETTINGS_PACKAGE = "com.android.settings";
    private static final List<String> BROWSER_PACKAGES = Arrays.asList(
            "com.android.chrome",
            "com.mi.globalbrowser",
            "com.huawei.browser",
            "com.heytap.browser",
            "com.vivo.browser"
    );

    public static boolean support() {
        try {
            CookieManager.getInstance();
            return App.get().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WEBVIEW);
        } catch (Throwable e) {
            return false;
        }
    }

    public static String spoof() {
        PackageManager pm = App.get().getPackageManager();
        for (String item : BROWSER_PACKAGES) if (installed(pm, item)) return item;
        return SYSTEM_SETTINGS_PACKAGE;
    }

    private static boolean installed(PackageManager pm, String pkg) {
        try {
            pm.getPackageInfo(pkg, PackageManager.GET_META_DATA);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}
