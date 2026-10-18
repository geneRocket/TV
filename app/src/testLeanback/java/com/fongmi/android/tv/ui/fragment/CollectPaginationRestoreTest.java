package com.fongmi.android.tv.ui.fragment;

import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.ui.custom.CustomScroller;

import org.junit.Test;

import static org.junit.Assert.*;

public class CollectPaginationRestoreTest {
    private CustomScroller restore(Collect collect) {
        CustomScroller restored = new CustomScroller(null);
        restored.restore(collect.getPage(), true);
        restored.setEnable(collect.getPageCount());
        return restored;
    }

    @Test public void emptyLastPageRemainsDisabledAfterViewRecreation() {
        Collect collect = new Collect();
        CustomScroller finished = new CustomScroller(null);
        finished.restore(2, false);
        CollectFragment.savePagination(collect, finished, 0);
        assertEquals(2, restore(collect).getPage());
        assertTrue(restore(collect).isDisable());
    }

    @Test public void freshPageCountReplacesStaleMetadata() {
        Collect collect = new Collect();
        collect.setPageCount(1);
        CustomScroller finished = new CustomScroller(null);
        finished.restore(2, true);
        CollectFragment.savePagination(collect, finished, 3);
        assertEquals(3, collect.getPageCount());
        assertFalse(restore(collect).isDisable());
    }

    @Test public void unknownPageCountWithMoreResultsStaysEnabled() {
        Collect collect = new Collect();
        CustomScroller finished = new CustomScroller(null);
        finished.restore(2, true);
        CollectFragment.savePagination(collect, finished, 0);
        assertEquals(0, collect.getPageCount());
        assertFalse(restore(collect).isDisable());
    }
}
