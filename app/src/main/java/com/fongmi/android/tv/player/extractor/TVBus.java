package com.fongmi.android.tv.player.extractor;

import android.net.Uri;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Core;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.player.Source;
import com.google.gson.JsonObject;
import com.tvbus.engine.Listener;
import com.tvbus.engine.TVCore;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TVBus implements Source.Extractor, Listener {

    private TVCore tvcore;
    private String hls;
    private Core core;
    private CountDownLatch latch;

    @Override
    public boolean match(Uri uri) {
        return "tvbus".equals(uri.getScheme());
    }

    private void init(Core core) {
        App.get().setHook(core.hook());
        tvcore = new TVCore(core.getSo());
        tvcore.auth(core.getAuth()).broker(core.getBroker());
        tvcore.name(core.getName()).pass(core.getPass());
        tvcore.serv(0).play(8902).mode(1).listener(this);
        App.get().setHook(false);
        tvcore.init();
    }

    @Override
    public String fetch(String url) throws Exception {
        if (core != null && !core.equals(LiveConfig.get().getHome().getCore())) change();
        if (tvcore == null) init(core = LiveConfig.get().getHome().getCore());
        hls = null;
        latch = new CountDownLatch(1);
        tvcore.start(url);
        onWait();
        onCheck();
        return hls;
    }

    private void onCheck() throws Exception {
        if (hls == null) throw new ExtractException("TVBus prepare timeout");
        if (hls.startsWith("-")) throw new ExtractException("Error Code : " + hls);
    }

    private void onWait() throws InterruptedException {
        if (latch != null) latch.await(Constant.TIMEOUT_PLAY, TimeUnit.MILLISECONDS);
    }

    private void onNotify() {
        if (latch != null) latch.countDown();
    }

    private void change() {
        Setting.putBootLive(true);
        App.post(() -> System.exit(0), 250);
    }

    @Override
    public void stop() {
        if (tvcore != null) tvcore.stop();
        if (hls != null) hls = null;
    }

    @Override
    public void exit() {
        if (tvcore != null) tvcore.quit();
        tvcore = null;
    }

    @Override
    public void onPrepared(String result) {
        JsonObject json = App.gson().fromJson(result, JsonObject.class);
        if (json.get("hls") == null) return;
        hls = json.get("hls").getAsString();
        onNotify();
    }

    @Override
    public void onStop(String result) {
        JsonObject json = App.gson().fromJson(result, JsonObject.class);
        hls = json.get("errno").getAsString();
        if (hls.startsWith("-")) onNotify();
    }

    @Override
    public void onInited(String result) {
    }

    @Override
    public void onStart(String result) {
    }

    @Override
    public void onInfo(String result) {
    }

    @Override
    public void onQuit(String result) {
    }
}
