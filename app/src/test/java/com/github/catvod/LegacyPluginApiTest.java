package com.github.catvod;

import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import org.junit.Test;
import java.io.File;
import java.io.InputStream;
import okhttp3.Dns;
import static org.junit.Assert.*;

public class LegacyPluginApiTest {
    @Test public void dnsRetainsItsReturnDescriptorForAlreadyCompiledPlugins() throws Exception {
        assertEquals(Dns.class, OkHttp.class.getMethod("dns").getReturnType());
    }

    @Test public void copyRetainsItsVoidDescriptorsForAlreadyCompiledPlugins() throws Exception {
        assertEquals(void.class, Path.class.getMethod("copy", File.class, File.class).getReturnType());
        assertEquals(void.class, Path.class.getMethod("copy", InputStream.class, File.class).getReturnType());
    }
}
