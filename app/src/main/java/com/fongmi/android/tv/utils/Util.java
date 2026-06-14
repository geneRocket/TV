package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcelable;
import android.provider.Settings;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.github.catvod.Init;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class Util {

    private static final Pattern PATTERN_DIGIT = Pattern.compile("\\D+");
    private static final Pattern PATTERN_VERSION = Pattern.compile("(?i)(mp4|H264|H265|720p|1080p|2160p|4K)");

    public static void toggleFullscreen(Activity activity, boolean fullscreen) {
        if (fullscreen) hideSystemUI(activity);
        else showSystemUI(activity);
    }

    public static void showSystemUI(Activity activity) {
        activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    public static void hideSystemUI(Activity activity) {
        hideSystemUI(activity.getWindow());
    }

    public static void hideSystemUI(Window window) {
        int flags = View.SYSTEM_UI_FLAG_LOW_PROFILE | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        window.getDecorView().setSystemUiVisibility(flags);
    }

    public static void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) App.get().getSystemService(Context.INPUT_METHOD_SERVICE);
        IBinder windowToken = view.getWindowToken();
        if (imm == null || windowToken == null) return;
        imm.hideSoftInputFromWindow(windowToken, 0);
    }

    public static float getBrightness(Activity activity) {
        try {
            float value = activity.getWindow().getAttributes().screenBrightness;
            if (WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL >= value && value >= WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF) return value;
            return Settings.System.getFloat(activity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 128;
        } catch (Exception e) {
            return 0.5f;
        }
    }

    public static CharSequence getClipText() {
        ClipboardManager manager = (ClipboardManager) App.get().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = manager == null ? null : manager.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() == 0) return "";
        return clipData.getItemAt(0).getText();
    }

    public static void copy(String text) {
        try {
            ClipboardManager manager = (ClipboardManager) App.get().getSystemService(Context.CLIPBOARD_SERVICE);
            manager.setPrimaryClip(ClipData.newPlainText("", text));
            Notify.show(R.string.copied);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int getDigit(String text) {
        try {
            if (TextUtils.isEmpty(text) || text.startsWith("上") || text.startsWith("下")) return -1;
            String value = PATTERN_VERSION.matcher(text).replaceAll("");
            String digit = PATTERN_DIGIT.matcher(value).replaceAll("");
            return digit.isEmpty() ? -1 : Integer.parseInt(digit);
        } catch (Exception e) {
            return -1;
        }
    }

    public static String getAndroidId() {
        try {
            String id = Settings.Secure.getString(Init.context().getContentResolver(), Settings.Secure.ANDROID_ID);
            if (TextUtils.isEmpty(id)) throw new NullPointerException();
            return id;
        } catch (Exception e) {
            return "0000000000000000";
        }
    }

    public static String getDeviceName() {
        String model = Build.MODEL;
        String manufacturer = Build.MANUFACTURER;
        return model.startsWith(manufacturer) ? model : manufacturer + " " + model;
    }

    public static String substring(String text) {
        return substring(text, 1);
    }

    public static String substring(String text, int num) {
        if (text != null && text.length() > num) return text.substring(0, text.length() - num);
        return text;
    }

    public static long format(SimpleDateFormat format, String src) {
        try {
            return format.parse(src).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    public static String format(SimpleDateFormat format, long time) {
        try {
            return format.format(time);
        } catch (Exception e) {
            return "";
        }
    }

    public static String format(StringBuilder builder, Formatter formatter, long timeMs) {
        try {
            return androidx.media3.common.util.Util.getStringForTime(builder, formatter, timeMs);
        } catch (Exception e) {
            return "";
        }
    }

    public static Intent getChooser(Intent intent) {
        List<ComponentName> components = new ArrayList<>();
        for (ResolveInfo resolveInfo : App.get().getPackageManager().queryIntentActivities(intent, 0)) {
            String pkgName = resolveInfo.activityInfo.packageName;
            if (pkgName.equals(App.get().getPackageName())) {
                components.add(new ComponentName(pkgName, resolveInfo.activityInfo.name));
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Intent.createChooser(intent, null).putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, components.toArray(new Parcelable[]{}));
        } else {
            return Intent.createChooser(intent, null);
        }
    }

    public static boolean hasSAFChooser() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        return intent.resolveActivity(App.get().getPackageManager()) != null;
    }

    public static boolean isTvBox() {
        PackageManager pm = App.get().getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) && !pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return true;
        }
        if (pm.hasSystemFeature("amazon.hardware.fire_tv")) {
            return true;
        }
        if (!hasSAFChooser()) {
            return true;
        }
        if (Build.VERSION.SDK_INT < 30) {
            if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                return true;
            }
            if (pm.hasSystemFeature("android.hardware.hdmi.cec")) {
                return true;
            }
            if (Build.MANUFACTURER.equalsIgnoreCase("zidoo")) {
                return true;
            }
        }
        return false;
    }

    public static int batteryLevel() {
        BatteryManager batteryManager = (BatteryManager) App.get().getSystemService(Context.BATTERY_SERVICE);
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    public static void restartApp(Activity activity) {
        Intent intent = activity.getBaseContext().getPackageManager().getLaunchIntentForPackage(activity.getBaseContext().getPackageName());
        ComponentName componentName = intent.getComponent();
        Intent mainIntent = Intent.makeRestartActivityTask(componentName);
        activity.startActivity(mainIntent);
        Runtime.getRuntime().exit(0);
    }

    public static Spanned fromHtml(String text) {
        if (TextUtils.isEmpty(text)) return Html.fromHtml("");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            return Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY);
        } else {
            return Html.fromHtml(text);
        }
    }

    public static String normalize(String text) {
        return TextUtils.isEmpty(text) ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s]+", " ").trim();
    }

    public static double similarity(String s1, String s2) {
        String str1 = normalize(s1);
        String str2 = normalize(s2);
        if (str1.isEmpty() || str2.isEmpty()) return 0;
        if (str1.equals(str2)) return 1.0;
        int len1 = str1.length();
        int len2 = str2.length();
        int[] dp = new int[len2 + 1];
        for (int j = 0; j <= len2; j++) dp[j] = j;
        for (int i = 1; i <= len1; i++) {
            int prev = i;
            for (int j = 1; j <= len2; j++) {
                int cur = (str1.charAt(i - 1) == str2.charAt(j - 1)) ? dp[j - 1] : Math.min(Math.min(dp[j - 1], dp[j]), prev) + 1;
                dp[j - 1] = prev;
                prev = cur;
            }
            dp[len2] = prev;
        }
        return 1.0 - (double) dp[len2] / Math.max(len1, len2);
    }

}
