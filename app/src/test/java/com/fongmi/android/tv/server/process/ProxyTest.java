package com.fongmi.android.tv.server.process;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class ProxyTest {

    @Test public void preservesValidStatusOutsideNanoHttpdEnum() throws Exception {
        ByteArrayInputStream body = new ByteArrayInputStream(new byte[]{42});
        try (Response response = Proxy.toResponse(new Object[]{425, "text/plain", body, Map.of("Retry-After", "10")})) {
            assertEquals(425, response.getStatus().getRequestStatus());
            assertEquals("10", response.getHeader("Retry-After"));
            assertEquals("text/plain", response.getMimeType());
            assertSame(body, response.getData());
        }
    }

    @Test public void returnsServerErrorForMissingResult() throws Exception {
        try (Response response = Proxy.toResponse(null)) {
            assertEquals(Status.INTERNAL_ERROR, response.getStatus());
        }
    }

    @Test public void ignoresMalformedOptionalHeaders() throws Exception {
        ByteArrayInputStream body = new ByteArrayInputStream(new byte[]{42});
        try (Response response = Proxy.toResponse(new Object[]{200, "text/plain", body, "invalid"})) {
            assertEquals(Status.OK, response.getStatus());
            assertSame(body, response.getData());
        }
    }

    @Test public void leavesHttpFramingToNanoHttpd() throws Exception {
        Map<String, String> headers = Map.of("content-length", "999", "Transfer-Encoding", "identity", "Content-Range", "bytes 0-0/2");
        try (Response response = Proxy.toResponse(new Object[]{206, "video/mp4", new ByteArrayInputStream(new byte[]{42}), headers})) {
            assertEquals(Status.PARTIAL_CONTENT, response.getStatus());
            assertEquals("bytes 0-0/2", response.getHeader("Content-Range"));
            assertNull(response.getHeader("Content-Length"));
            assertNull(response.getHeader("Transfer-Encoding"));
        }
    }

    @Test public void retainsValidHeadersFromMixedMap() throws Exception {
        Map<String, Object> headers = new HashMap<>();
        headers.put("Location", "https://example.com/stream");
        headers.put("Invalid", 42);
        try (Response response = Proxy.toResponse(new Object[]{302, null, null, headers})) {
            assertEquals("https://example.com/stream", response.getHeader("Location"));
            assertNull(response.getHeader("Invalid"));
        }
    }

    @Test public void preservesNullBodyNotFoundResponse() throws Exception {
        try (Response response = Proxy.toResponse(new Object[]{404, "text/plain", null})) {
            assertEquals(Status.NOT_FOUND, response.getStatus());
            assertEquals(-1, response.getData().read());
        }
    }

    @Test public void passesThroughNativeResponse() throws Exception {
        try (Response response = NanoHTTPD.newFixedLengthResponse("ok")) {
            assertSame(response, Proxy.toResponse(new Object[]{response}));
        }
    }
}
