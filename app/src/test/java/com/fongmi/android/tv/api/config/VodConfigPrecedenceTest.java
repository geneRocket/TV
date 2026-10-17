package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.bean.Site;
import org.junit.Test;
import java.util.Collections;
import java.util.Map;
import static org.junit.Assert.*;

public class VodConfigPrecedenceTest {
    @Test public void duplicateKeyKeepsFirstCompleteSource() {
        VodConfig owner = new VodConfig();
        Site first = new PureSite("7@source", "csp_First", "first extension", "first.jar");
        owner.addSite(first, "", Collections.emptyMap());
        owner.addSite(new PureSite("7@source", "csp_Second", "second extension", "second.jar"), "", Collections.emptyMap());
        assertEquals(1, owner.getSites().size());
        assertSame(first, owner.getSites().get(0));
        assertEquals("csp_First", first.getApi());
        assertEquals("first extension", first.getExt());
        assertEquals("first.jar", first.getJar());
    }

    @Test public void sameRawKeyInDifferentConfigurationsKeepsBothSources() {
        VodConfig owner = new VodConfig();
        Site first = new PureSite("7@source", "csp_First", "first extension", "first.jar");
        Site second = new PureSite("8@source", "csp_Second", "second extension", "second.jar");
        owner.addSite(first, "", Collections.emptyMap());
        owner.addSite(second, "", Collections.emptyMap());
        assertEquals(2, owner.getSites().size());
        assertSame(first, owner.getSites().get(0));
        assertSame(second, owner.getSites().get(1));
    }

    private static class PureSite extends Site {
        private final String key;
        private String api, ext, jar;
        PureSite(String key, String api, String ext, String jar) {
            this.key = key;
            this.api = api;
            this.ext = ext;
            this.jar = jar;
        }
        @Override public boolean isEmpty() { return false; }
        @Override public String getKey() { return key; }
        @Override public String getApi() { return api; }
        @Override public String getExt() { return ext; }
        @Override public String getJar() { return jar; }
        @Override public void setApi(String value) { api = value; }
        @Override public void setExt(String value) { ext = value; }
        @Override public void setJar(String value) { jar = value; }
        @Override public Site trans() { return this; }
        @Override public Site sync(Map<String, Site> cache) { return this; }
    }
}
