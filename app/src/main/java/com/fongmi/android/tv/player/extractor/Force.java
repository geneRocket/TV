package com.fongmi.android.tv.player.extractor;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.SystemClock;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.Source;
import com.forcetech.Util;
import com.github.catvod.net.OkHttp;
import com.google.common.net.HttpHeaders;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Force implements Source.Extractor {

    private static final long WAIT_MILLIS = 5000;

    private final Set<String> set = ConcurrentHashMap.newKeySet();

    @Override
    public boolean match(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme();
        return "mitv".equals(scheme) || (!"push".equals(scheme) && !"proxy".equals(scheme) && scheme.startsWith("p"));
    }

    private void init(String scheme) {
        App.get().bindService(Util.intent(App.get(), scheme), mConn, Context.BIND_AUTO_CREATE);
    }

    @Override
    public String fetch(String url) throws Exception {
        String scheme = Util.scheme(url);
        if (!set.contains(scheme)) init(scheme);
        long start = SystemClock.elapsedRealtime();
        while (!set.contains(scheme)) {
            if (SystemClock.elapsedRealtime() - start > WAIT_MILLIS) throw new IllegalStateException("Force service connect timeout: " + scheme);
            SystemClock.sleep(10);
        }
        Uri uri = Uri.parse(url);
        int port = Util.port(scheme);
        String id = uri.getLastPathSegment();
        String cmd = "http://127.0.0.1:" + port + "/cmd.xml?cmd=switch_chan&server=" + uri.getHost() + ":" + uri.getPort() + "&id=" + id;
        String result = "http://127.0.0.1:" + port + "/" + id;
        Map<String, String> headers = new HashMap<>();
        headers.put(HttpHeaders.USER_AGENT, "MTV");
        OkHttp.string(cmd, headers);
        return result;
    }

    @Override
    public void stop() {
    }

    @Override
    public void exit() {
        try {
            if (!set.isEmpty()) App.get().unbindService(mConn);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            set.clear();
        }
    }

    private final ServiceConnection mConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            set.add(Util.trans(name));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            set.remove(Util.trans(name));
        }
    };
}
