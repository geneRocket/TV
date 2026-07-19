package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.databinding.ActivitySettingPlayerBinding;
import com.fongmi.android.tv.impl.BufferCallback;
import com.fongmi.android.tv.impl.UaCallback;
import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.BufferDialog;
import com.fongmi.android.tv.ui.dialog.UaDialog;
import com.fongmi.android.tv.utils.ResUtil;

public class SettingPlayerActivity extends BaseActivity implements UaCallback, BufferCallback {

    private ActivitySettingPlayerBinding mBinding;
    private String[] caption;
    private String[] player;
    private String[] decode;
    private String[] render;
    private String[] scale;
    private String[] http;
    private String[] flag;
    private String[] rtsp;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingPlayerActivity.class));
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
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingPlayerBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        setVisible();
        mBinding.player.requestFocus();
        mBinding.uaText.setText(Setting.getUa());
        mBinding.tunnelText.setText(getSwitch(Setting.isTunnel()));
        mBinding.bufferText.setText(Setting.getBufferText());
        mBinding.rtspText.setText((rtsp = ResUtil.getStringArray(R.array.select_rtsp))[safeIndex(Setting.getRtsp(), rtsp)]);
        mBinding.flagText.setText((flag = ResUtil.getStringArray(R.array.select_flag))[safeIndex(Setting.getFlag(), flag)]);
        mBinding.httpText.setText((http = ResUtil.getStringArray(R.array.select_exo_http))[safeIndex(Setting.getHttp(), http)]);
        mBinding.scaleText.setText((scale = ResUtil.getStringArray(R.array.select_scale))[safeIndex(Setting.getScale(), scale)]);
        mBinding.playerText.setText((player = ResUtil.getStringArray(R.array.select_player))[safeIndex(Setting.getPlayer(), player)]);
        mBinding.decodeText.setText((decode = ResUtil.getStringArray(R.array.select_decode))[safeIndex(Setting.getDecode(Setting.getPlayer()), decode)]);
        mBinding.renderText.setText((render = ResUtil.getStringArray(R.array.select_render))[safeIndex(Setting.getRender(), render)]);
        mBinding.captionText.setText((caption = ResUtil.getStringArray(R.array.select_caption))[safeIndex(Setting.isCaption() ? 1 : 0, caption)]);
    }

    @Override
    protected void initEvent() {
        mBinding.ua.setOnClickListener(this::onUa);
        mBinding.rtsp.setOnClickListener(this::setRtsp);
        mBinding.http.setOnClickListener(this::setHttp);
        mBinding.flag.setOnClickListener(this::setFlag);
        mBinding.scale.setOnClickListener(this::setScale);
        mBinding.buffer.setOnClickListener(this::onBuffer);
        mBinding.player.setOnClickListener(this::setPlayer);
        mBinding.decode.setOnClickListener(this::setDecode);
        mBinding.render.setOnClickListener(this::setRender);
        mBinding.tunnel.setOnClickListener(this::setTunnel);
        mBinding.caption.setOnClickListener(this::setCaption);
        mBinding.caption.setOnLongClickListener(this::onCaption);
    }

    private void setVisible() {
        mBinding.caption.setVisibility(Setting.hasCaption() ? View.VISIBLE : View.GONE);
        mBinding.http.setVisibility(Players.isExo(Setting.getPlayer()) ? View.VISIBLE : View.GONE);
        mBinding.buffer.setVisibility(Players.isExo(Setting.getPlayer()) ? View.VISIBLE : View.GONE);
        mBinding.tunnel.setVisibility(Players.isExo(Setting.getPlayer()) ? View.VISIBLE : View.GONE);
    }

    private void onUa(View view) {
        UaDialog.create(this).show();
    }

    @Override
    public void setUa(String ua) {
        mBinding.uaText.setText(ua);
        Setting.putUa(ua);
    }

    private void setRtsp(View view) {
        int index = nextIndex(Setting.getRtsp(), rtsp);
        Setting.putRtsp(index);
        mBinding.rtspText.setText(rtsp[index]);
    }

    private void setHttp(View view) {
        int index = nextIndex(Setting.getHttp(), http);
        Setting.putHttp(index);
        mBinding.httpText.setText(http[index]);
    }

    private void setFlag(View view) {
        int index = nextIndex(Setting.getFlag(), flag);
        Setting.putFlag(index);
        mBinding.flagText.setText(flag[index]);
    }

    private void setScale(View view) {
        int index = nextIndex(Setting.getScale(), scale);
        Setting.putScale(index);
        mBinding.scaleText.setText(scale[index]);
    }

    private void onBuffer(View view) {
        BufferDialog.create(this).show();
    }

    @Override
    public void setBuffer(int buffer) {
        mBinding.bufferText.setText(Setting.getBufferText(buffer));
        Setting.putBuffer(buffer);
    }

    private void setPlayer(View view) {
        int index = nextIndex(Setting.getPlayer(), player);
        Setting.putPlayer(index);
        mBinding.playerText.setText(player[index]);
        mBinding.decodeText.setText(decode[safeIndex(Setting.getDecode(index), decode)]);
        setVisible();
    }

    private void setDecode(View view) {
        int player = Setting.getPlayer();
        int index = nextIndex(Setting.getDecode(player), decode);
        Setting.putDecode(player, index);
        mBinding.decodeText.setText(decode[index]);
    }

    private void setRender(View view) {
        int index = nextIndex(Setting.getRender(), render);
        Setting.putRender(index);
        mBinding.renderText.setText(render[index]);
        if (Setting.isTunnel() && Setting.getRender() == 1) setTunnel(view);
    }

    private void setTunnel(View view) {
        Setting.putTunnel(!Setting.isTunnel());
        mBinding.tunnelText.setText(getSwitch(Setting.isTunnel()));
        if (Setting.isTunnel() && Setting.getRender() == 1) setRender(view);
    }

    private void setCaption(View view) {
        Setting.putCaption(!Setting.isCaption());
        mBinding.captionText.setText(caption[safeIndex(Setting.isCaption() ? 1 : 0, caption)]);
    }

    private boolean onCaption(View view) {
        if (Setting.isCaption()) startActivity(new Intent(Settings.ACTION_CAPTIONING_SETTINGS));
        return Setting.isCaption();
    }

}
