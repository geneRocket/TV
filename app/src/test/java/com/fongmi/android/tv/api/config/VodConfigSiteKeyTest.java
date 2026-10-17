package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.bean.History;
import org.junit.Test;
import static org.junit.Assert.*;

public class VodConfigSiteKeyTest {
    @Test public void numericRawKeysOutsideConfigIdRangeKeepTheirIdentity() {
        String raw = "202610170001@source";
        assertFalse(VodConfig.isScopedSiteKey(raw));
        assertEquals(7, VodConfig.siteCid(raw, 7));
        assertEquals(raw, VodConfig.rawSiteKey(raw));
        assertEquals(raw + "@@@video@@@7", History.historyKey(raw, "video", "name", 7));
    }

    @Test public void validScopedKeysStillResolveTheirConfiguration() {
        assertTrue(VodConfig.isScopedSiteKey("2147483647@source"));
        assertEquals(Integer.MAX_VALUE, VodConfig.siteCid("2147483647@source", 7));
        assertEquals("source", VodConfig.rawSiteKey("7@source"));
        assertEquals("source@@@video@@@7", History.historyKey("7@source", "video", "name", 7));
        assertFalse(VodConfig.isScopedSiteKey(null));
        assertFalse(VodConfig.isScopedSiteKey("source@raw"));
    }
}
