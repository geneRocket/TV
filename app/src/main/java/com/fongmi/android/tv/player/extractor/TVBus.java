package com.fongmi.android.tv.player.extractor;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Core;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.utils.ThreadPools;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.google.common.net.InternetDomainName;
import com.google.gson.JsonObject;
import com.tvbus.engine.Listener;
import com.tvbus.engine.TVCore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class TVBus implements Source.Extractor {

    private static final int DOMAIN_LOOKUP_TIMEOUT_SECONDS = 3;
    private static final int MAX_DOMAIN_BODY_BYTES = 256;

    private volatile Engine engine;
    private Core core;

    @Override
    public boolean match(Uri uri) {
        return "tvbus".equals(uri.getScheme());
    }

    private Engine init(Core core) throws InterruptedException {
        String domain = UrlUtil.convert(core.getDomain());
        String scheme = UrlUtil.scheme(domain);
        if ("http".equals(scheme) || "https".equals(scheme)) domain = readDomainSuffix(OkHttp.client(), domain);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        Engine next;
        App.get().setHook(core.hook());
        try {
            TVCore tvcore = new TVCore(core.getSo());
            next = new Engine(tvcore);
            tvcore.auth(core.getAuth()).broker(core.getBroker()).domain(domain);
            tvcore.name(core.getName()).pass(core.getPass());
            tvcore.serv(0).play(8902).mode(1).listener(next);
            for (Core.Option option : core.getOptions()) {
                if (option == null) continue;
                for (String value : option.getValues()) tvcore.option(option.getKey(), value);
            }
        } finally {
            App.get().setHook(false);
        }
        next.tvcore.init();
        return next;
    }

    static String readDomainSuffix(OkHttpClient client, String url) {
        Call call;
        try {
            call = OkHttp.newCall(client, url);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
        call.timeout().timeout(DOMAIN_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        CompletableFuture<String> suffix = new CompletableFuture<>();
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException error) {
                suffix.complete("");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (response) {
                    suffix.complete(parseDomainSuffix(response));
                } catch (IOException ignored) {
                    suffix.complete("");
                }
            }
        });
        try {
            return suffix.get(DOMAIN_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException | TimeoutException ignored) {
            return "";
        } finally {
            if (!suffix.isDone()) call.cancel();
        }
    }

    private static String parseDomainSuffix(Response response) throws IOException {
        if (!response.isSuccessful() || response.body() == null) return "";
        byte[] body = response.peekBody(MAX_DOMAIN_BODY_BYTES + 1L).bytes();
        if (body.length > MAX_DOMAIN_BODY_BYTES) return "";
        String value = new String(body, StandardCharsets.UTF_8).trim();
        String domain = value.startsWith(".") ? value.substring(1) : value;
        return InternetDomainName.isValid(domain) ? value : "";
    }

    @Override
    public String fetch(String url) throws Exception {
        Core nextCore = LiveConfig.get().getHome().getCore();
        if (nextCore == null) throw new ExtractException("TVBus core is not configured");
        Engine active = engine;
        if (active == null || !nextCore.equals(core)) {
            reset();
            active = init(nextCore);
            core = nextCore;
            engine = active;
        }
        return active.fetch(url);
    }

    private void reset() {
        Engine previous = engine;
        engine = null;
        core = null;
        if (previous == null) return;
        try {
            previous.stop();
        } catch (Throwable error) {
            ThreadPools.log(error, "TVBus stop failed while switching core.");
        }
        try {
            previous.tvcore.quit();
        } catch (Throwable error) {
            ThreadPools.log(error, "TVBus quit failed while switching core.");
        }
    }

    @Override
    public void stop() {
        Engine active = engine;
        if (active != null) active.stop();
    }

    @Override
    public void exit() {
        reset();
    }

    private static final class Engine implements Listener {

        private final TVCore tvcore;
        private volatile String hls;
        private volatile CountDownLatch latch;

        private Engine(TVCore tvcore) {
            this.tvcore = tvcore;
        }

        private String fetch(String url) throws Exception {
            hls = null;
            CountDownLatch ready = latch = new CountDownLatch(1);
            tvcore.start(url);
            ready.await(Constant.TIMEOUT_PLAY, TimeUnit.MILLISECONDS);
            String result = hls;
            if (result == null) throw new ExtractException("TVBus prepare timeout");
            if (result.startsWith("-")) throw new ExtractException("Error Code : " + result);
            return result;
        }

        private void stop() {
            tvcore.stop();
            hls = null;
        }

        private void onNotify() {
            CountDownLatch ready = latch;
            if (ready != null) ready.countDown();
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

        @Override public void onInited(String result) { }
        @Override public void onStart(String result) { }
        @Override public void onInfo(String result) { }
        @Override public void onQuit(String result) { }
    }
}
