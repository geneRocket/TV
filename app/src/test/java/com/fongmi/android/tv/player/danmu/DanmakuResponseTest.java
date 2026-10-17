package com.fongmi.android.tv.player.danmu;

import okhttp3.*;
import org.junit.Test;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class DanmakuResponseTest {
    private static final String XML = "<i><d p='1,1,25,16777215'>中文弹幕</d></i>";

    @Test public void httpCharsetControlsLegacyChineseDecoding() throws Exception {
        assertEquals(XML, read(XML.getBytes(Charset.forName("GB18030")), "application/xml; charset=GB18030", 200));
    }

    @Test public void unicodeBomControlsDecodingWithoutACharsetHeader() throws Exception {
        assertEquals(XML, read(XML.getBytes(StandardCharsets.UTF_16), "application/xml", 200));
    }

    @Test public void utf8ContentKeepsWorking() throws Exception {
        assertEquals(XML, read(XML.getBytes(StandardCharsets.UTF_8), "application/xml", 200));
    }

    @Test public void oversizedContentAndFailedResponsesStillReturnEmpty() throws Exception {
        assertEquals("", read(new byte[2 * 1024 * 1024 + 1], "application/xml", 200));
        assertEquals("", read(XML.getBytes(StandardCharsets.UTF_8), "application/xml", 403));
    }

    private String read(byte[] body, String type, int code) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
                new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(code).message("test").body(ResponseBody.create(MediaType.get(type), body)).build()).build();
        try {
            return Parser.request(client.newCall(new Request.Builder().url("http://localhost/danmaku").build()));
        } finally {
            client.connectionPool().evictAll(); client.dispatcher().executorService().shutdownNow();
        }
    }
}
