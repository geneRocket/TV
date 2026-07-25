package com.fongmi.android.tv.ui.base;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.fongmi.android.tv.utils.ResUtil;

public abstract class BaseActivity extends AbsActivity {

    @Override
    public boolean isLeanback() {
        return false;
    }

    @Override
    protected void initStatus() {
        if (transparent()) setTransparent(this);
    }

    protected boolean transparent() {
        return true;
    }

    public void setPadding(ViewGroup layout) {
        setPadding(layout, false);
    }

    public void setPadding(ViewGroup layout, boolean leftOnly) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        DisplayCutout cutout = ResUtil.getDisplay(this).getCutout();
        if (cutout == null) return;
        int top = cutout.getSafeInsetTop();
        int left = cutout.getSafeInsetLeft();
        int right = cutout.getSafeInsetRight();
        int bottom = cutout.getSafeInsetBottom();
        int padding = left | right | top | bottom;
        layout.setPadding(padding, 0, leftOnly ? 0 : padding, 0);
    }

    public void noPadding(ViewGroup layout) {
        if (layout != null) layout.setPadding(0, 0, 0, 0);
    }

    private void setTransparent(Activity activity) {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
    }
}
