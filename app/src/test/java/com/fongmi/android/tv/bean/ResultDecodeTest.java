package com.fongmi.android.tv.bean;
import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class ResultDecodeTest {
    @Test public void successMessagesDoNotRejectPlaybackOrDownloads() {
        Gson gson = new Gson();
        assertFalse(Result.objectFrom("{\"code\":1,\"msg\":\"获取成功\",\"url\":\"https://example.com/video.mp4\"}", gson).hasErrorMsg());
        assertFalse(Result.objectFrom("{\"code\":200,\"msg\":\"OK\"}", gson).hasErrorMsg());
        assertTrue(Result.objectFrom("{\"code\":0,\"msg\":\"播放失败\"}", gson).hasErrorMsg());
        assertTrue(Result.objectFrom("{\"msg\":\"播放失败\"}", gson).hasErrorMsg());
        assertFalse(Result.objectFrom("{}", gson).hasErrorMsg());
    }

    @Test public void validEmptyResponseIsDistinctFromMalformedOrNullPayload() {
        Gson gson = new Gson();
        assertFalse(Result.objectFrom("{\"list\":[]}", gson).isRequestFailed());
        assertTrue(Result.objectFrom("<html>blocked</html>", gson).isRequestFailed());
        assertTrue(Result.objectFrom("{\"list\":[", gson).isRequestFailed());
        assertTrue(Result.objectFrom("null", gson).isRequestFailed());
    }
}
