package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.api.config.VodConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class HistorySyncScopeTest {
    private Config previous;

    @Before public void changeActiveConfiguration() {
        previous = VodConfig.get().getConfig();
        Config active = new Config(); active.setId(20);
        VodConfig.get().config(active);
    }

    @After public void restoreConfiguration() { VodConfig.get().config(previous); }

    @Test public void importDestinationDoesNotFollowTheActiveConfiguration() {
        Incoming target = new Incoming(Collections.emptyList());
        sync(10, Collections.singletonList(target));
        assertEquals(10, target.savedCid);
        assertEquals(10, target.queriedCid);
    }

    @Test public void newerRecordInAnotherConfigurationDoesNotSuppressImport() {
        Incoming target = new Incoming(Collections.singletonList(local(20, 300)));
        sync(10, Collections.singletonList(target));
        assertEquals(10, target.savedCid);
        assertTrue(target.merged.isEmpty());
    }

    @Test public void newerLocalRecordIsPreservedAndOlderLocalRecordCanBeUpdated() {
        Incoming target = new Incoming(Arrays.asList(local(10, 300), local(20, 100)));
        sync(10, Collections.singletonList(target));
        assertEquals(0, target.savedCid);
        Incoming older = new Incoming(Collections.singletonList(local(10, 100)));
        sync(10, Collections.singletonList(older));
        assertEquals(10, older.savedCid);
        assertEquals(1, older.merged.size());
        assertEquals(10, older.merged.get(0).getCid());
    }

    private static void sync(int cid, List<History> targets) {
        com.fongmi.android.tv.repository.HistoryStore.startSync(cid, targets,
                new com.fongmi.android.tv.repository.HistoryStore.SyncAccess() {
                    @Override public List<History> find(History target, int config) { return ((Incoming) target).find(config); }
                    @Override public void update(History target, int config, List<History> items) { ((Incoming) target).update(config, items); }
                });
    }

    private static History local(int cid, long time) {
        History value = new History(); value.setCid(cid); value.setCreateTime(time); return value;
    }

    private static class Incoming extends History {
        final List<History> existing;
        int queriedCid, savedCid;
        List<History> merged;
        Incoming(List<History> existing) { this.existing = existing; setCreateTime(200); }
        @Override public List<History> find() { return existing; }
        @Override List<History> find(int cid) {
            queriedCid = cid;
            List<History> result = new ArrayList<>();
            for (History item : existing) if (item.getCid() == cid) result.add(item);
            return result;
        }
        @Override public History update(int cid, List<History> items) {
            savedCid = cid; merged = items; return this;
        }
    }
}
