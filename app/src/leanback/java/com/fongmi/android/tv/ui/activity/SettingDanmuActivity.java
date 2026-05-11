package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.databinding.ActivitySettingDanmuBinding;
import com.fongmi.android.tv.impl.DanmuAlphaCallback;
import com.fongmi.android.tv.impl.DanmuLineCallback;
import com.fongmi.android.tv.impl.DanmuSizeCallback;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.ResUtil;

public class SettingDanmuActivity extends BaseActivity implements DanmuLineCallback, DanmuSizeCallback, DanmuAlphaCallback {

    private ActivitySettingDanmuBinding mBinding;

    private String[] danmuSpeed;

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingDanmuBinding.inflate(getLayoutInflater());
    }

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingDanmuActivity.class));
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    private int safeIndex(int index, String[] items) {
        if (items == null || items.length == 0) return 0;
        return Math.max(0, Math.min(index, items.length - 1));
    }

    private int nextIndex(int index, String[] items) {
        if (items == null || items.length == 0) return 0;
        index = safeIndex(index, items);
        return index == items.length - 1 ? 0 : index + 1;
    }

    @Override
    protected void initView() {
        mBinding.danmuLoad.requestFocus();
        ((TextView) mBinding.danmuLoad.getChildAt(0)).setText(R.string.play_danmu);
        mBinding.danmuLoadText.setText(getSwitch(Setting.isDanmu()));
        mBinding.danmuSizeText.setText(String.valueOf(Setting.getDanmuSize()));
        mBinding.danmuLineText.setText(String.valueOf(Setting.getDanmuLine(3)));
        mBinding.danmuAlphaText.setText(String.valueOf(Setting.getDanmuAlpha()));
        mBinding.danmuSpeedText.setText((danmuSpeed = ResUtil.getStringArray(R.array.select_danmu_speed))[safeIndex(Setting.getDanmuSpeed(), danmuSpeed)]);
    }

    @Override
    protected void initEvent() {
        mBinding.danmuSize.setOnClickListener(this::onDanmuSize);
        mBinding.danmuLine.setOnClickListener(this::onDanmuLine);
        mBinding.danmuLoad.setOnClickListener(this::setDanmuLoad);
        mBinding.danmuAlpha.setOnClickListener(this::onDanmuAlpha);
        mBinding.danmuSpeed.setOnClickListener(this::setDanmuSpeed);
        mBinding.danmuSize.setOnKeyListener(this::onDanmuSizeKey);
        mBinding.danmuLine.setOnKeyListener(this::onDanmuLineKey);
        mBinding.danmuAlpha.setOnKeyListener(this::onDanmuAlphaKey);
        mBinding.danmuSpeed.setOnKeyListener(this::onDanmuSpeedKey);
    }

    private void onDanmuSize(View view) {
        float size = Setting.getDanmuSize();
        if (size >= 2.0f) size = 0.6f;
        else size += 0.2f;
        setDanmuSize((float) (Math.round(size * 10.0) / 10.0));
    }

    private boolean onDanmuSizeKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (KeyUtil.isLeftKey(event)) {
            setDanmuSize((float) (Math.round(Math.max(0.6f, Setting.getDanmuSize() - 0.2f) * 10.0) / 10.0));
            return true;
        } else if (KeyUtil.isRightKey(event)) {
            setDanmuSize((float) (Math.round(Math.min(2.0f, Setting.getDanmuSize() + 0.2f) * 10.0) / 10.0));
            return true;
        }
        return false;
    }

    @Override
    public void setDanmuSize(float size) {
        mBinding.danmuSizeText.setText(String.valueOf(size));
        Setting.putDanmuSize(size);
    }

    private void onDanmuLine(View view) {
        int line = Setting.getDanmuLine(3);
        if (line >= 15) line = 1;
        else line++;
        setDanmuLine(line);
    }

    private boolean onDanmuLineKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (KeyUtil.isLeftKey(event)) {
            setDanmuLine(Math.max(1, Setting.getDanmuLine(3) - 1));
            return true;
        } else if (KeyUtil.isRightKey(event)) {
            setDanmuLine(Math.min(15, Setting.getDanmuLine(3) + 1));
            return true;
        }
        return false;
    }

    private void setDanmuLoad(View view) {
        Setting.putDanmu(!Setting.isDanmu());
        mBinding.danmuLoadText.setText(getSwitch(Setting.isDanmu()));
    }

    @Override
    public void setDanmuLine(int line) {
        mBinding.danmuLineText.setText(String.valueOf(line));
        Setting.putDanmuLine(line);
    }

    private void onDanmuAlpha(View view) {
        int alpha = Setting.getDanmuAlpha();
        if (alpha >= 100) alpha = 10;
        else alpha += 10;
        setDanmuAlpha(alpha);
    }

    private boolean onDanmuAlphaKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (KeyUtil.isLeftKey(event)) {
            setDanmuAlpha(Math.max(10, Setting.getDanmuAlpha() - 10));
            return true;
        } else if (KeyUtil.isRightKey(event)) {
            setDanmuAlpha(Math.min(100, Setting.getDanmuAlpha() + 10));
            return true;
        }
        return false;
    }

    @Override
    public void setDanmuAlpha(int alpha) {
        mBinding.danmuAlphaText.setText(String.valueOf(alpha));
        Setting.putDanmuAlpha(alpha);
    }

    private void setDanmuSpeed(View view) {
        int index = nextIndex(Setting.getDanmuSpeed(), danmuSpeed);
        Setting.putDanmuSpeed(index);
        mBinding.danmuSpeedText.setText(danmuSpeed[index]);
    }

    private boolean onDanmuSpeedKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (KeyUtil.isLeftKey(event)) {
            int index = safeIndex(Setting.getDanmuSpeed() - 1, danmuSpeed);
            Setting.putDanmuSpeed(index);
            mBinding.danmuSpeedText.setText(danmuSpeed[index]);
            return true;
        } else if (KeyUtil.isRightKey(event)) {
            int index = safeIndex(Setting.getDanmuSpeed() + 1, danmuSpeed);
            Setting.putDanmuSpeed(index);
            mBinding.danmuSpeedText.setText(danmuSpeed[index]);
            return true;
        }
        return false;
    }


}
