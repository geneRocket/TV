package com.fongmi.android.tv.server;

import com.fongmi.android.tv.player.Players;
import com.github.catvod.Proxy;
import com.github.catvod.utils.Util;

public class Server {

    // The local HTTP server reads this from its worker threads while playback
    // activities replace it on the main thread.
    private volatile Players player;
    private Nano nano;
    private int port;

    private static class Loader {
        static volatile Server INSTANCE = new Server();
    }

    public static Server get() {
        return Loader.INSTANCE;
    }

    public Server() {
        this.port = 9978;
    }

    public int getPort() {
        return port;
    }

    public Players getPlayer() {
        return player;
    }

    public void setPlayer(Players player) {
        this.player = player;
    }

    public String getAddress() {
        return getAddress(false);
    }

    public String getAddress(int tab) {
        return getAddress(false) + "?tab=" + tab;
    }

    public String getAddress(String path) {
        return getAddress(true) + path;
    }

    public String getAddress(boolean local) {
        return "http://" + (local ? "127.0.0.1" : Util.getIp()) + ":" + getPort();
    }

    /**
     * The application prewarms the server in the background while activities can
     * request it later. Serialise those callers so a second starter cannot race
     * the first one for the same port.
     */
    public synchronized void start() {
        if (nano != null) return;
        int maxAttempts = 20;
        int attempt = 0;
        while (port < 9999 && attempt < maxAttempts) {
            try {
                nano = new Nano(port);
                nano.start();
                Proxy.set(port);
                break;
            } catch (Exception e) {
                if (nano != null) nano.stop();
                nano = null;
                port++;
                attempt++;
            }
        }
    }

    public synchronized void stop() {
        if (nano != null) nano.stop();
        nano = null;
    }
}
