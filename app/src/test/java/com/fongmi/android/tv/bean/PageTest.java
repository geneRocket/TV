package com.fongmi.android.tv.bean;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class PageTest {
    @Test public void folderReturnRetainsIndependentFilterSnapshot() {
        Map<String, String> filter = new HashMap<>();
        filter.put("year", "2025");
        Vod folder = new Vod() { @Override public String getVodId() { return "folder"; } };
        Page<Object, Object> page = Page.get(folder, 4, new Object(), new Object(), 2, true).context(filter, true);
        filter.put("year", "2026");
        assertEquals("2025", page.getExtend().get("year"));
        assertFalse(page.getExtend().equals(filter));
        assertTrue(page.isFilterOpen());
        assertEquals(2, page.getPage());
    }
}
