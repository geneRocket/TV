package com.fongmi.android.tv.ui.base;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;

public abstract class AbsActivity extends AppCompatActivity {

    protected abstract ViewBinding getBinding();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initStatus();
        setContentView(getBinding().getRoot());
        EventBus.getDefault().register(this);
        if (isLeanback()) Util.hideSystemUI(this);
        setBackCallback();
        initView();
        initView(savedInstanceState);
        initEvent();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        refreshWall();
    }

    public Activity getActivity() {
        return this;
    }

    public boolean isLeanback() {
        return false;
    }

    protected void initStatus() {
    }

    protected boolean customWall() {
        return true;
    }

    protected boolean handleBack() {
        return false;
    }

    protected void initView() {
    }

    protected void initView(Bundle savedInstanceState) {
    }

    protected void initEvent() {
    }

    protected void onBackPress() {
        finish();
    }

    public boolean isVisible(View view) {
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    public boolean isGone(View view) {
        return view != null && view.getVisibility() == View.GONE;
    }

    protected void setBackCallback() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(handleBack()) {
            @Override
            public void handleOnBackPressed() {
                onBackPress();
            }
        });
    }

    private static Drawable WALL_CACHE;
    private static int WALL_INDEX = -1;

    protected void refreshWall() {
        try {
            if (!customWall()) return;
            int index = Setting.getWall();
            if (WALL_CACHE != null && WALL_INDEX == index) {
                getWindow().setBackgroundDrawable(WALL_CACHE);
                return;
            }
            File file = FileUtil.getWall(index);
            if (file.exists() && file.length() > 0) {
                WALL_CACHE = Drawable.createFromPath(file.getAbsolutePath());
                WALL_INDEX = index;
                getWindow().setBackgroundDrawable(WALL_CACHE);
            } else {
                WALL_CACHE = null;
                WALL_INDEX = -1;
                int resId = ResUtil.getDrawable(file.getName());
                getWindow().setBackgroundDrawableResource(resId == 0 ? R.drawable.wallpaper_1 : resId);
            }
        } catch (Exception e) {
            getWindow().setBackgroundDrawableResource(R.drawable.wallpaper_1);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.WALL) {
            WALL_CACHE = null;
            WALL_INDEX = -1;
            refreshWall();
        }
    }

    @Override
    public Resources getResources() {
        Resources resources = super.getResources();
        if (isLeanback()) {
            try {
                Class<?> clazz = Class.forName("me.jessyan.autosize.AutoSizeCompat");
                java.lang.reflect.Method method = clazz.getMethod("autoConvertDensityOfGlobal", Resources.class);
                method.invoke(null, resources);
            } catch (Exception ignored) {
            }
        }
        return resources;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isLeanback()) Util.hideSystemUI(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && isLeanback()) Util.hideSystemUI(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
