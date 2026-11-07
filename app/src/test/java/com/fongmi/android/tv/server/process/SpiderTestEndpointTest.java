package com.fongmi.android.tv.server.process;

import com.fongmi.android.tv.server.Nano;

import org.junit.Test;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static org.junit.Assert.assertEquals;

public class SpiderTestEndpointTest {

    @Test public void diagnosticsRejectBrowserRequestsWithoutOptInHeader() throws Exception {
        assertDiagnosticStatus(403, false, null, "op=search");
    }

    @Test public void diagnosticsRejectBrowserOriginEvenWithOptInHeader() throws Exception {
        assertDiagnosticStatus(403, true, "https://example.com", "site=demo&op=home");
    }

    @Test public void diagnosticsValidateArgumentsBeforeCallingSite() throws Exception {
        assertDiagnosticStatus(400, true, null, "site=demo&op=player");
    }

    private static void assertDiagnosticStatus(int expected, boolean optIn, String origin, String query) throws Exception {
        Nano nano = new Nano(0);
        nano.start(2000, true);
        try {
            Request.Builder request = new Request.Builder()
                    .url("http://127.0.0.1:" + nano.getListeningPort() + "/spider-test?" + query)
                    .post(RequestBody.create(new byte[0]));
            if (optIn) request.header("X-TV-Spider-Test", "1");
            if (origin != null) request.header("Origin", origin);
            try (Response response = new OkHttpClient().newCall(request.build()).execute()) {
                assertEquals(expected, response.code());
            }
        } finally {
            nano.stop();
        }
    }
}
