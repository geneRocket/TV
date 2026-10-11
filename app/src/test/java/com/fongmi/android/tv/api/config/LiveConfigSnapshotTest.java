package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.bean.Live;
import org.junit.Test;
import java.util.Iterator;
import java.util.List;
import static org.junit.Assert.*;

public class LiveConfigSnapshotTest {
    @Test public void backgroundAppendDoesNotInvalidateUiIteration() throws Exception {
        LiveConfig config = new LiveConfig();
        java.lang.reflect.Field field = LiveConfig.class.getDeclaredField("lives");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") List<Live> owner = (List<Live>) field.get(config);
        Live first = new Live();
        owner.add(first);
        Iterator<Live> visible = config.getLives().iterator();
        owner.add(new Live());
        assertSame(first, visible.next());
        assertFalse(visible.hasNext());
        assertEquals(2, config.getLives().size());
    }
}
