package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.bean.History;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class VodHistoryPersistenceTest {
    @Test public void lifecycleSaveOwnsASnapshotAndMarksTheCurrentLoadAsLocallyModified() {
        Writer writer = new Writer(); VodHistoryController controller = new VodHistoryController(writer, () -> 10_000);
        History current = history(), loaded = history(); loaded.setPosition(1);
        controller.beginLoad();
        assertTrue(controller.saveNow(current, true, 480_000, 900_000));
        current.setPosition(0);
        assertEquals(480_000, writer.saved.get(0).getPosition());
        assertEquals(10_000, writer.saved.get(0).getCreateTime());
        assertSame(current, controller.applyLoaded(current, loaded));
    }
    @Test public void incognitoAndUnprovenRoutesCannotWriteOrModifyPersistedProgress() {
        for (boolean incognito : new boolean[]{false, true}) {
            Writer writer = new Writer(); writer.enabled = !incognito;
            VodHistoryController controller = new VodHistoryController(writer, () -> 10_000);
            History current = history();
            assertFalse(controller.saveNow(current, incognito, 0, 900_000));
            assertEquals(120_000, current.getPosition());
            assertTrue(writer.saved.isEmpty());
        }
    }
    @Test public void progressTicksThrottleDiskWritesWithoutLosingTheLatestInMemoryPosition() {
        Writer writer = new Writer(); VodHistoryController controller = new VodHistoryController(writer, () -> 10_000);
        History current = history();
        assertTrue(controller.saveTick(current, true, 130_000, 900_000, 10_000));
        assertFalse(controller.saveTick(current, true, 140_000, 900_000, 11_000));
        assertEquals(140_000, current.getPosition());
        assertTrue(controller.saveTick(current, true, 150_000, 900_000, 13_000));
        assertFalse(controller.saveTick(current, true, 150_000, 900_000, 16_000));
        assertEquals(2, writer.saved.size());
        assertEquals(130_000, writer.saved.get(0).getPosition());
    }
    @Test public void aNewLoadDoesNotInheritThePreviousLoadsMutationFlag() {
        Writer writer = new Writer(); VodHistoryController controller = new VodHistoryController(writer, () -> 10_000);
        History current = history(), loaded = history();
        controller.saveNow(current, true, 480_000, 900_000);
        controller.beginLoad();
        assertSame(loaded, controller.applyLoaded(current, loaded));
    }
    private static History history() { History h = new History(); h.setKey("site@@@video@@@7"); h.setCid(7); h.setPosition(120_000); return h; }
    private static final class Writer implements VodHistoryController.Persistence {
        boolean enabled = true;
        final List<History> saved = new ArrayList<>();
        @Override public boolean enabled() { return enabled; }
        @Override public void save(History snapshot, boolean async) { saved.add(snapshot); }
    }
}
