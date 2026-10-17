package com.fongmi.android.tv.repository;

import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.impl.Callback;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static org.junit.Assert.*;

public class VodEntryResolverTest {
    @Test public void loadedSiteOpensWithoutReloadingConfigurations() {
        Harness h = new Harness();
        h.sites.add("2@site");
        h.open(2);
        assertEquals(1, h.played);
        assertTrue(h.worker.isEmpty());
    }

    @Test public void inactiveOwnerLoadsAlongsideExistingSelectionsBeforePlayback() {
        Harness h = new Harness();
        h.open(2);
        h.drain();
        assertEquals(Arrays.asList(1, 2), h.loadedIds());
        assertEquals(0, h.played);
        assertEquals(0, h.fallbacks);
        h.sites.add("2@site");
        h.callback.success();
        assertEquals(0, h.played);
        h.drain();
        assertEquals(1, h.played);
        assertEquals(1, h.changes);
    }

    @Test public void deletedOwnerFallsBackToSearch() {
        Harness h = new Harness();
        h.owner = null;
        h.open(2);
        h.drain();
        assertEquals(1, h.fallbacks);
        assertNull(h.loaded);
    }

    @Test public void alreadySelectedOwnerIsNotDuplicated() {
        Harness h = new Harness();
        h.selected = Arrays.asList(config(1), config(2));
        h.open(2);
        h.drain();
        assertEquals(Arrays.asList(1, 2), h.loadedIds());
    }

    @Test public void newerClickSuppressesOldLoadCompletion() {
        Harness h = new Harness();
        h.open(2);
        h.drain();
        assertNotNull(h.callback);
        Callback old = h.callback;
        h.sites.add("3@site");
        h.open(3);
        h.sites.add("2@site");
        old.success();
        h.drain();
        assertEquals(1, h.played);
    }

    @Test public void cancelledLookupCannotChangeConfigurations() {
        Harness h = new Harness();
        h.open(2);
        h.resolver.cancel();
        h.drain();
        assertNull(h.loaded);
        assertEquals(0, h.fallbacks);
    }

    @Test public void closingPageSuppressesQueuedSuccessAndError() {
        Harness h = new Harness();
        h.open(2);
        h.drain();
        assertNotNull(h.callback);
        h.sites.add("2@site");
        h.callback.success();
        h.callback.error("load failed");
        h.ready = false;
        h.drain();
        assertEquals(0, h.played);
        assertEquals(0, h.errors);
    }

    @Test public void missingSiteAfterLoadFallsBackAndLoadErrorsAreReported() {
        Harness h = new Harness();
        h.open(2);
        h.drain();
        assertNotNull(h.callback);
        h.callback.success();
        h.drain();
        assertEquals(1, h.fallbacks);
        h.open(2);
        h.drain();
        h.callback.error("load failed");
        h.drain();
        assertEquals(1, h.errors);
    }

    private static Config config(int id) {
        Config config = new Config();
        config.setId(id);
        config.setType(0);
        config.setUrl("https://config.example/" + id);
        return config;
    }

    private static class Harness implements VodEntryResolver.Backend {
        final Queue<Runnable> worker = new ArrayDeque<>();
        final Queue<Runnable> main = new ArrayDeque<>();
        final Set<String> sites = new HashSet<>();
        boolean ready = true;
        final VodEntryResolver resolver = new VodEntryResolver(() -> ready, this);
        int played, fallbacks, errors, changes;
        Config owner = config(2);
        List<Config> selected = Collections.singletonList(config(1));
        List<Config> loaded;
        Callback callback;

        void open(int id) { resolver.open(id, id + "@site", () -> played++, () -> fallbacks++, msg -> errors++); }
        void drain() {
            while (!worker.isEmpty() || !main.isEmpty()) {
                while (!worker.isEmpty()) worker.remove().run();
                while (!main.isEmpty()) main.remove().run();
            }
        }
        List<Integer> loadedIds() {
            assertNotNull(loaded);
            List<Integer> ids = new ArrayList<>();
            for (Config config : loaded) ids.add(config.getId());
            return ids;
        }
        @Override public boolean hasSite(String key) { return sites.contains(key); }
        @Override public Config find(int id) { return owner; }
        @Override public List<Config> selected() { return selected; }
        @Override public void execute(Runnable task) { worker.add(task); }
        @Override public void post(Runnable task) { main.add(task); }
        @Override public void load(List<Config> configs, Callback result) { loaded = configs; callback = result; }
        @Override public void changed() { changes++; }
    }
}
