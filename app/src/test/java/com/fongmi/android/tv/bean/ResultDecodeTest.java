package com.fongmi.android.tv.bean;
import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class ResultDecodeTest {
    @Test public void validEmptyResponseIsDistinctFromMalformedOrNullPayload() {
        Gson gson = new Gson();
        assertFalse(Result.objectFrom("{\"list\":[]}", gson).isRequestFailed());
        assertTrue(Result.objectFrom("<html>blocked</html>", gson).isRequestFailed());
        assertTrue(Result.objectFrom("{\"list\":[", gson).isRequestFailed());
        assertTrue(Result.objectFrom("null", gson).isRequestFailed());
    }
}
