package com.fongmi.android.tv.ui.adapter;

import com.fongmi.android.tv.bean.Config;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ConfigAdapterRefreshTest {
    private Config config(String desc) {
        return new Config() {
            @Override public int getId() { return 1; }
            @Override public String getUrl() { return "https://config.example/main.json"; }
            @Override public String getDesc() { return desc; }
        };
    }

    @Test public void metadataRefreshReplacesSameConfigurationIdentity() throws Exception {
        ConfigAdapter adapter = new ConfigAdapter(null) {
            { TvAdapterIdentityTest.initializeObservers(this); }
            @Override public void setHasStableIds(boolean stable) {
                TvAdapterIdentityTest.initializeObservers(this);
                super.setHasStableIds(stable);
            }
        };
        adapter.setItems(Collections.singletonList(config("before")));
        adapter.setItems(Collections.singletonList(config("after")));
        Field field = ConfigAdapter.class.getDeclaredField("mItems");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") List<Config> rows = (List<Config>) field.get(adapter);
        assertEquals("after", rows.get(0).getDesc());
    }
}
