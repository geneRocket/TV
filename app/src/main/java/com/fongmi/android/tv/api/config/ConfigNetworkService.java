package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.Setting;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;

/** Applies the combined network policy; config parsers never mutate the HTTP client. */
public final class ConfigNetworkService {
    private ConfigNetworkService() {}
    public static synchronized void refresh() {
        ConfigRuntime.Snapshot snapshot = ConfigRuntime.snapshot();
        VodConfig vod = snapshot.vod;
        LiveConfig live = snapshot.live;
        OkHttp.clearConfig();
        OkHttp.get().setProxy(Setting.getProxy());
        OkHttp.get().setDoh(Doh.objectFrom(Setting.getDoh()));
        OkHttp.responseInterceptor().addAll(vod.getHeaders());
        OkHttp.responseInterceptor().addAll(live.getHeaders());
        OkHttp.authenticator().addAll(vod.getProxy());
        OkHttp.authenticator().addAll(live.getProxy());
        OkHttp.selector().addProxyAll(vod.getProxy());
        OkHttp.selector().addProxyAll(live.getProxy());
        OkHttp.resolver().addAll(vod.getHosts());
        OkHttp.resolver().addAll(live.getHosts());
        OkHttp.selector().addAll(vod.getRuleHosts());
        OkHttp.selector().addAll(live.getRuleHosts());
    }
}
