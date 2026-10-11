package com.fongmi.android.tv.api.loader;

import com.fongmi.android.tv.utils.BoundedCache;
import com.fongmi.quickjs.bean.Req;
import com.fongmi.quickjs.utils.Module;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScriptCacheTest {
    @Test public void customRequestOptionsCannotReuseDefaultModuleResponse() {
        assertTrue(Req.objectFrom("{}").canUseModuleCache());
        assertFalse(Req.objectFrom("{\"headers\":{\"Authorization\":\"Bearer a\"}}").canUseModuleCache());
        assertFalse(Req.objectFrom("{\"headers\":{\"Content-Type\":\"text/plain;charset=GBK\"}}").canUseModuleCache());
        assertFalse(Req.objectFrom("{\"redirect\":0}").canUseModuleCache());
        assertFalse(Req.objectFrom("{\"buffer\":1}").canUseModuleCache());
        assertFalse(Req.objectFrom("{\"method\":\"post\"}").canUseModuleCache());
    }

    @Test public void clearingDiskAlsoInvalidatesOldAndNewModuleMemory() throws Exception {
        Module old = Module.get();
        java.lang.reflect.Field field = Module.class.getDeclaredField("cache");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") BoundedCache<String, String> cache = (BoundedCache<String, String>) field.get(old);
        cache.put("assets://test", "old script");
        assertEquals("old script", old.getCached("assets://test"));
        Module.clearCache(() -> assertNull(old.getCached("assets://test")));
        assertNotSame(old, Module.get());
        assertNull(old.getCached("assets://test"));
        assertNull(Module.get().getCached("assets://test"));
    }
}
