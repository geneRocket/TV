package com.fongmi.android.tv.server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import okhttp3.HttpUrl;

/** Matches a requested overwrite pull to its legacy HTTP callback. */
public final class SyncPulls {
    static final long MAX_AGE_NANOS = TimeUnit.MINUTES.toNanos(2);
    private static final SyncPulls INSTANCE = new SyncPulls(System::nanoTime);
    private final LongSupplier clock;
    private final Map<Key, Ticket> pending = new LinkedHashMap<>();

    public static SyncPulls get() { return INSTANCE; }
    SyncPulls(LongSupplier clock) { this.clock = clock; }

    public synchronized Ticket begin(String peer, String type, String configUrl) {
        long now = clock.getAsLong();
        prune(now);
        Ticket ticket = new Ticket(key(peer, type, configUrl), now);
        if (ticket.key != null) {
            pending.remove(ticket.key);
            pending.put(ticket.key, ticket);
            while (pending.size() > 64) pending.remove(pending.keySet().iterator().next());
        }
        return ticket;
    }

    public synchronized boolean consume(String peer, String type, String configUrl) {
        prune(clock.getAsLong());
        Key key = key(peer, type, configUrl);
        return key != null && pending.remove(key) != null;
    }

    private void prune(long now) {
        pending.values().removeIf(ticket -> now - ticket.created > MAX_AGE_NANOS);
    }

    private Key key(String peer, String type, String configUrl) {
        if (peer == null || peer.isEmpty()) return null;
        if (!"history".equals(type) && !"keep".equals(type)) return null;
        String address = peer;
        if (!peer.contains("://")) {
            boolean ipv6 = peer.indexOf(':') != peer.lastIndexOf(':') && !peer.startsWith("[");
            address = "http://" + (ipv6 ? "[" + peer + "]" : peer);
        }
        HttpUrl url = HttpUrl.parse(address);
        String host = url == null ? peer : url.host();
        return new Key(host, type, "history".equals(type) && configUrl != null ? configUrl.trim() : "");
    }

    private static final class Key {
        final String peer, type, url;
        Key(String peer, String type, String url) { this.peer = peer; this.type = type; this.url = url; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return peer.equals(key.peer) && type.equals(key.type) && url.equals(key.url);
        }
        @Override public int hashCode() { return Objects.hash(peer, type, url); }
    }

    public final class Ticket implements AutoCloseable {
        private final Key key;
        private final long created;
        Ticket(Key key, long created) { this.key = key; this.created = created; }
        @Override public void close() {
            synchronized (SyncPulls.this) {
                if (pending.get(key) == this) pending.remove(key);
            }
        }
    }
}
