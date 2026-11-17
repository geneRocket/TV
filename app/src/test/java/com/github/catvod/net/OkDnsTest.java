package com.github.catvod.net;

import com.github.catvod.bean.Doh;

import org.junit.Test;

import java.util.Collections;

import okhttp3.Dns;

import static org.junit.Assert.assertEquals;

public class OkDnsTest {

    @Test public void malformedEndpointFallsBackToSystemDnsAndKeepsHostOverrides() throws Exception {
        OkDns dns = new OkDns();
        dns.addAll(Collections.singletonList("stream.invalid=127.0.0.1"));
        dns.setDoh(endpoint("https://resolver.invalid/dns-query"));
        dns.setDoh(endpoint("not-a-url"));
        assertEquals(Dns.SYSTEM.lookup("127.0.0.1"), dns.lookup("stream.invalid"));
    }

    @Test public void literalHostOverridesWorkWithDohEnabled() throws Exception {
        OkDns dns = new OkDns();
        dns.addAll(Collections.singletonList("stream.invalid=127.0.0.1"));
        dns.setDoh(endpoint("https://127.0.0.1:1/dns-query"));
        assertEquals(Dns.SYSTEM.lookup("127.0.0.1"), dns.lookup("stream.invalid"));
    }

    private static Doh endpoint(String url) {
        return new Doh() {
            @Override public String getUrl() {
                return url;
            }
        };
    }
}
